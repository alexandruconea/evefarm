package com.evefarm.service;

import com.evefarm.db.dao.CharacterDao;
import com.evefarm.model.EveCharacter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AggroWatchService {

    private static final Logger LOG = Logger.getLogger(AggroWatchService.class.getName());
    private static final Pattern LOG_FILE = Pattern.compile("^\\d{8}_\\d{6}_(\\d+)\\.txt$");
    private static final long TAIL_MILLIS = 250;
    private static final Duration RESCAN_INTERVAL = Duration.ofSeconds(5);
    private static final int MAX_READ_BYTES = 1024 * 1024;
    static final Duration QUIET_TIME = Duration.ofSeconds(15);

    public record Alert(String characterName, String attacker, Instant at) {
    }

    public interface Listener {
        void onAggro(Alert alert);
    }

    private static final class TailedLog {
        final long characterId;
        final Path file;
        long offset;
        final ByteArrayOutputStream partial = new ByteArrayOutputStream();

        TailedLog(long characterId, Path file, long offset) {
            this.characterId = characterId;
            this.file = file;
            this.offset = offset;
        }
    }

    private final Supplier<Path> directory;
    private final Supplier<Map<Long, String>> characters;
    private final Clock clock;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Map<Long, TailedLog> logs = new HashMap<>();
    private final Map<Long, Instant> lastHostile = new HashMap<>();
    private ScheduledExecutorService executor;
    private ScheduledFuture<?> task;
    private Instant lastScan;
    private boolean firstScan;

    public AggroWatchService(KillService killService, CharacterDao characterDao) {
        this(killService::gameLogDirectory, () -> {
            Map<Long, String> names = new HashMap<>();
            for (EveCharacter character : characterDao.listAll()) {
                names.put(character.characterId(), character.characterName());
            }
            return names;
        }, Clock.systemUTC());
    }

    AggroWatchService(Supplier<Path> directory, Supplier<Map<Long, String>> characters, Clock clock) {
        this.directory = directory;
        this.characters = characters;
        this.clock = clock;
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public synchronized void start() {
        if (task != null) {
            return;
        }
        reset();
        if (executor == null) {
            executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "aggro-watch");
                thread.setDaemon(true);
                return thread;
            });
        }
        task = executor.scheduleWithFixedDelay(this::tickSafely, 0, TAIL_MILLIS, TimeUnit.MILLISECONDS);
    }

    public synchronized void stop() {
        if (task != null) {
            task.cancel(false);
            task = null;
        }
        reset();
    }

    synchronized void reset() {
        logs.clear();
        lastHostile.clear();
        lastScan = null;
        firstScan = true;
    }

    private void tickSafely() {
        try {
            tick();
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Aggro watch failed", e);
        }
    }

    void tick() {
        List<Alert> alerts = new ArrayList<>();
        synchronized (this) {
            Instant now = clock.instant();
            if (lastScan == null || Duration.between(lastScan, now).compareTo(RESCAN_INTERVAL) >= 0) {
                rescan();
                lastScan = now;
            }
            for (TailedLog log : logs.values()) {
                for (String line : readNewLines(log)) {
                    AggroLineParser.attacker(line).ifPresent(attacker -> {
                        Instant previous = lastHostile.put(log.characterId, now);
                        if (previous == null || Duration.between(previous, now).compareTo(QUIET_TIME) > 0) {
                            alerts.add(new Alert(nameOf(log.characterId), attacker, now));
                        }
                    });
                }
            }
        }
        for (Alert alert : alerts) {
            for (Listener listener : listeners) {
                try {
                    listener.onAggro(alert);
                } catch (RuntimeException e) {
                    LOG.log(Level.WARNING, "Aggro listener failed", e);
                }
            }
        }
    }

    private String nameOf(long characterId) {
        String name = characters.get().get(characterId);
        return name == null ? "Character " + characterId : name;
    }

    private void rescan() {
        Path dir = directory.get();
        if (dir == null || !Files.isDirectory(dir)) {
            return;
        }
        Map<Long, String> known = characters.get();
        Map<Long, Path> newest = new HashMap<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.txt")) {
            for (Path file : files) {
                Matcher name = LOG_FILE.matcher(file.getFileName().toString());
                if (!name.matches()) {
                    continue;
                }
                long characterId = Long.parseLong(name.group(1));
                if (!known.containsKey(characterId)) {
                    continue;
                }
                Path current = newest.get(characterId);
                if (current == null || file.getFileName().toString().compareTo(current.getFileName().toString()) > 0) {
                    newest.put(characterId, file);
                }
            }
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.FINE, "Couldn't list the Gamelogs in " + dir, e);
            return;
        }
        for (Map.Entry<Long, Path> entry : newest.entrySet()) {
            TailedLog current = logs.get(entry.getKey());
            if (current != null && current.file.equals(entry.getValue())) {
                continue;
            }
            long offset = firstScan ? sizeOf(entry.getValue()) : 0;
            logs.put(entry.getKey(), new TailedLog(entry.getKey(), entry.getValue(), offset));
        }
        firstScan = false;
    }

    private static long sizeOf(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return 0;
        }
    }

    private static List<String> readNewLines(TailedLog log) {
        long size = sizeOf(log.file);
        if (size < log.offset) {
            log.offset = 0;
            log.partial.reset();
        }
        if (size == log.offset) {
            return List.of();
        }
        int length = (int) Math.min(size - log.offset, MAX_READ_BYTES);
        ByteBuffer buffer = ByteBuffer.allocate(length);
        try (SeekableByteChannel channel = Files.newByteChannel(log.file, StandardOpenOption.READ)) {
            channel.position(log.offset);
            int read = 0;
            while (buffer.hasRemaining() && read >= 0) {
                read = channel.read(buffer);
            }
        } catch (IOException e) {
            return List.of();
        }
        log.offset += buffer.position();
        byte[] bytes = new byte[buffer.position()];
        buffer.flip();
        buffer.get(bytes);
        List<String> lines = new ArrayList<>();
        for (byte b : bytes) {
            if (b == '\n') {
                lines.add(log.partial.toString(StandardCharsets.UTF_8));
                log.partial.reset();
            } else {
                log.partial.write(b);
            }
        }
        return lines;
    }
}
