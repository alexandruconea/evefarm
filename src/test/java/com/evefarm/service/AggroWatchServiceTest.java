package com.evefarm.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AggroWatchServiceTest {

    private static final long NOZEU = 2122680043L;
    private static final long BARSET = 2124083817L;
    private static final long STRANGER = 99L;
    private static final Instant START = Instant.parse("2026-09-28T11:20:00Z");
    private static final String HIT = "[ 2026.09.28 11:21:43 ] (combat) 13 from Lucifer Dramiel - Hits\r\n";
    private static final String MISS = "[ 2026.09.28 11:21:44 ] (combat) Lucifer Echo misses you completely\r\n";
    private static final String OWN_SHOT = "[ 2026.09.28 11:21:44 ] (combat) 212 to Lucifer Echo - Penetrates\r\n";

    @TempDir
    Path logs;

    private final MutableClock clock = new MutableClock(START);
    private final List<AggroWatchService.Alert> alerts = new ArrayList<>();
    private AggroWatchService watch;

    @BeforeEach
    void setUp() {
        watch = new AggroWatchService(() -> logs, () -> Map.of(NOZEU, "Nozeu", BARSET, "Barset"), clock);
        watch.addListener(alerts::add);
        watch.reset();
    }

    @Test
    void onlyShotsFiredAfterWatchingStartsAreAnnounced() throws IOException {
        Path nozeu = log("20260928_111429_" + NOZEU, header("Nozeu") + HIT + MISS);
        watch.tick();
        assertEquals(List.of(), alerts);

        append(nozeu, OWN_SHOT + HIT);
        watch.tick();

        assertEquals(List.of(new AggroWatchService.Alert("Nozeu", "Lucifer Dramiel", START)), alerts);
    }

    @Test
    void aCharacterUnderFireIsAnnouncedOnceUntilItHasBeenLeftAlone() throws IOException {
        Path nozeu = log("20260928_111429_" + NOZEU, header("Nozeu"));
        Path barset = log("20260928_111419_" + BARSET, header("Barset"));
        watch.tick();

        append(nozeu, HIT);
        tickAt(1);
        append(nozeu, MISS + HIT);
        tickAt(10);
        append(barset, MISS);
        tickAt(12);
        append(nozeu, HIT);
        tickAt(10 + AggroWatchService.QUIET_TIME.toSeconds() + 1);

        assertEquals(List.of("Nozeu", "Barset", "Nozeu"),
                alerts.stream().map(AggroWatchService.Alert::characterName).toList());
        assertEquals("Lucifer Echo", alerts.get(1).attacker());
    }

    @Test
    void aLineIsReadOnlyOnceItIsComplete() throws IOException {
        Path nozeu = log("20260928_111429_" + NOZEU, header("Nozeu"));
        watch.tick();

        append(nozeu, HIT.substring(0, 30));
        watch.tick();
        assertEquals(List.of(), alerts);

        append(nozeu, HIT.substring(30));
        watch.tick();
        assertEquals(1, alerts.size());
    }

    @Test
    void aNewGameSessionIsFollowedFromItsFirstLine() throws IOException {
        log("20260928_052652_" + NOZEU, header("Nozeu"));
        watch.tick();

        log("20260928_111429_" + NOZEU, header("Nozeu") + MISS);
        log("20260928_111500_" + STRANGER, header("Someone Else") + HIT);
        tickAt(6);

        assertEquals(List.of(new AggroWatchService.Alert("Nozeu", "Lucifer Echo", START.plusSeconds(6))),
                alerts);
    }

    private void tickAt(long secondsAfterStart) {
        clock.set(START.plusSeconds(secondsAfterStart));
        watch.tick();
    }

    private Path log(String name, String content) throws IOException {
        return Files.writeString(logs.resolve(name + ".txt"), content, StandardCharsets.UTF_8);
    }

    private static void append(Path file, String text) throws IOException {
        Files.writeString(file, text, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
    }

    private static String header(String listener) {
        return "------------------------------------------------------------\r\n  Gamelog\r\n  Listener: " + listener
                + "\r\n  Session Started: 2026.09.28 11:14:29\r\n------------------------------------------------------------\r\n\r\n";
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void set(Instant instant) {
            now = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
