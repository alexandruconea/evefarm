package com.evefarm.service;

import com.evefarm.db.dao.SettingsDao;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class EveSettingsService {

    private static final Pattern CHARACTER_FILE = Pattern.compile("core_char_(\\d+)\\.dat");
    private static final Pattern ACCOUNT_FILE = Pattern.compile("core_user_(\\d+)\\.dat");
    private static final DateTimeFormatter BACKUP_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);

    private final SettingsDao settingsDao;
    private final BooleanSupplier eveRunning;

    public EveSettingsService(SettingsDao settingsDao) {
        this(settingsDao, EveSettingsService::detectRunningClient);
    }

    EveSettingsService(SettingsDao settingsDao, BooleanSupplier eveRunning) {
        this.settingsDao = settingsDao;
        this.eveRunning = eveRunning;
    }

    public boolean isEveRunning() {
        return eveRunning.getAsBoolean();
    }

    public Optional<Path> findSettingsDirectory() {
        Optional<Path> configured = settingsDao.get(SettingsDao.EVE_SETTINGS_DIRECTORY)
                .filter(value -> !value.isBlank())
                .map(Path::of)
                .flatMap(this::resolveTranquilityDirectory);
        if (configured.isPresent()) {
            return configured;
        }

        String home = System.getProperty("user.home", "");
        if (home.isBlank()) {
            return Optional.empty();
        }
        Path userHome = Path.of(home);
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        List<Path> candidates = new ArrayList<>();
        if (os.contains("win")) {
            candidates.add(userHome.resolve("AppData/Local/CCP/EVE"));
        } else if (os.contains("mac")) {
            candidates.add(userHome.resolve("Library/Application Support/CCP/EVE"));
        } else {
            candidates.add(userHome.resolve(".local/share/Steam/steamapps/compatdata/8500/pfx/drive_c/users/steamuser/AppData/Local/CCP/EVE"));
            candidates.add(userHome.resolve(".steam/steam/steamapps/compatdata/8500/pfx/drive_c/users/steamuser/AppData/Local/CCP/EVE"));
        }
        return candidates.stream()
                .map(this::resolveTranquilityDirectory)
                .flatMap(Optional::stream)
                .max(Comparator.comparingLong(this::newestCharacterSettingsTimestamp));
    }

    public Path setSettingsDirectory(Path selected) throws IOException {
        Path resolved = resolveTranquilityDirectory(selected)
                .orElseThrow(() -> new IOException("The selected folder does not contain an EVE Tranquility settings profile."));
        settingsDao.set(SettingsDao.EVE_SETTINGS_DIRECTORY, resolved.toString());
        return resolved;
    }

    public ScanResult scan() throws IOException {
        Path root = findSettingsDirectory()
                .orElseThrow(() -> new IOException("EVE settings were not found. Select the folder ending in _tranquility."));
        return scan(root);
    }

    public ScanResult scan(Path selected) throws IOException {
        Path root = resolveTranquilityDirectory(selected)
                .orElseThrow(() -> new IOException("The folder does not contain EVE Tranquility settings."));
        List<Profile> profiles = new ArrayList<>();
        try (DirectoryStream<Path> directories = Files.newDirectoryStream(root, "settings_*")) {
            for (Path directory : directories) {
                if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(directory)) {
                    continue;
                }
                Map<Long, SettingsFile> characters = new LinkedHashMap<>();
                Map<Long, SettingsFile> accounts = new LinkedHashMap<>();
                try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.dat")) {
                    for (Path file : files) {
                        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)) {
                            continue;
                        }
                        Path filename = file.getFileName();
                        if (filename == null) {
                            continue;
                        }
                        Matcher character = CHARACTER_FILE.matcher(filename.toString());
                        Matcher account = ACCOUNT_FILE.matcher(filename.toString());
                        if (character.matches()) {
                            addSettingsFile(characters, Long.parseLong(character.group(1)), file);
                        } else if (account.matches()) {
                            addSettingsFile(accounts, Long.parseLong(account.group(1)), file);
                        }
                    }
                }
                if (!characters.isEmpty() || !accounts.isEmpty()) {
                    Path profileName = directory.getFileName();
                    if (profileName == null) {
                        continue;
                    }
                    profiles.add(new Profile(profileName.toString().substring("settings_".length()),
                            directory.toAbsolutePath().normalize(), List.copyOf(characters.values()),
                            List.copyOf(accounts.values())));
                }
            }
        }
        profiles.sort(Comparator.comparing(Profile::name, String.CASE_INSENSITIVE_ORDER));
        if (profiles.isEmpty()) {
            throw new IOException("No settings_* profiles containing core_char or core_user files were found.");
        }
        return new ScanResult(root.toAbsolutePath().normalize(), List.copyOf(profiles));
    }

    public CopyResult copy(CopyRequest request) throws IOException {
        if (isEveRunning()) {
            throw new IOException("EVE Online is running. Close every EVE client before copying settings.");
        }
        if ((request.sourceAccount() == null) != (request.targetAccount() == null)) {
            throw new IOException("Both source and target account files are required for account settings.");
        }

        Path root = requireRoot(request.settingsRoot());
        List<FileCopy> copies = new ArrayList<>();
        copies.add(validatedCopy(root, request.sourceCharacter(), request.targetCharacter(), CHARACTER_FILE,
                "character"));
        if (request.sourceAccount() != null) {
            Path sourceAccount = request.sourceAccount().toAbsolutePath().normalize();
            Path targetAccount = request.targetAccount().toAbsolutePath().normalize();
            if (!sourceAccount.equals(targetAccount)) {
                copies.add(validatedCopy(root, sourceAccount, targetAccount, ACCOUNT_FILE, "account"));
            }
        }

        Map<Path, Path> staged = new LinkedHashMap<>();
        Map<Path, Path> backups = new LinkedHashMap<>();
        List<Path> committed = new ArrayList<>();
        Path backupDirectory = null;
        try {
            for (FileCopy copy : copies) {
                Path targetParent = copy.target().getParent();
                if (targetParent == null) {
                    throw new IOException("Target settings file has no parent directory: " + copy.target());
                }
                Path temporary = Files.createTempFile(targetParent,
                        copy.target().getFileName() + ".evefarm-", ".tmp");
                Files.copy(copy.source(), temporary, StandardCopyOption.REPLACE_EXISTING);
                forceFile(temporary);
                verifyIdentical(copy.source(), temporary);
                staged.put(copy.target(), temporary);
            }

            if (copies.stream().anyMatch(copy -> Files.exists(copy.target(), LinkOption.NOFOLLOW_LINKS))) {
                backupDirectory = createBackupDirectory(root);
                for (FileCopy copy : copies) {
                    if (!Files.exists(copy.target(), LinkOption.NOFOLLOW_LINKS)) {
                        continue;
                    }
                    Path backup = backupDirectory.resolve(root.relativize(copy.target()));
                    Path backupParent = backup.getParent();
                    if (backupParent == null) {
                        throw new IOException("Backup file has no parent directory: " + backup);
                    }
                    Files.createDirectories(backupParent);
                    Files.copy(copy.target(), backup, StandardCopyOption.COPY_ATTRIBUTES);
                    verifyIdentical(copy.target(), backup);
                    backups.put(copy.target(), backup);
                }
            }

            for (FileCopy copy : copies) {
                replace(staged.remove(copy.target()), copy.target());
                committed.add(copy.target());
            }
            return new CopyResult(List.copyOf(committed), Optional.ofNullable(backupDirectory));
        } catch (Exception failure) {
            IOException error = failure instanceof IOException io ? io : new IOException("Copying EVE settings failed", failure);
            rollback(committed, backups, error);
            throw error;
        } finally {
            for (Path temporary : staged.values()) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private FileCopy validatedCopy(Path root, Path source, Path target, Pattern expectedName, String kind)
            throws IOException {
        Path realSource = validateSettingsPath(root, source.toAbsolutePath().normalize(), expectedName, kind, true);
        Path realTarget = validateSettingsPath(root, target.toAbsolutePath().normalize(), expectedName, kind, false);
        if (realSource.equals(realTarget)) {
            throw new IOException("Source and target " + kind + " files are the same.");
        }
        return new FileCopy(realSource, realTarget);
    }

    private static Path validateSettingsPath(Path root, Path path, Pattern expectedName, String kind,
                                             boolean mustExist) throws IOException {
        Path filename = path.getFileName();
        if (filename == null || !expectedName.matcher(filename.toString()).matches()) {
            throw new IOException("Invalid EVE " + kind + " settings filename: " + filename);
        }
        Path parent = path.getParent();
        if (parent == null || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(parent)) {
            throw new IOException("Invalid EVE settings profile directory: " + parent);
        }
        Path realParent = parent.toRealPath(LinkOption.NOFOLLOW_LINKS);
        Path profileParent = realParent.getParent();
        Path profileName = realParent.getFileName();
        if (!root.equals(profileParent) || profileName == null || !profileName.toString().startsWith("settings_")) {
            throw new IOException("The " + kind + " settings file is outside the selected EVE profile.");
        }
        if (mustExist && (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path))) {
            throw new IOException("Source EVE " + kind + " settings file does not exist: " + path);
        }
        if (!mustExist && Files.exists(path, LinkOption.NOFOLLOW_LINKS)
                && (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path))) {
            throw new IOException("Target EVE " + kind + " settings path is not a regular file: " + path);
        }
        return realParent.resolve(filename);
    }

    private static Path requireRoot(Path root) throws IOException {
        if (root == null || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) {
            throw new IOException("The EVE settings directory is unavailable.");
        }
        Path real = root.toRealPath(LinkOption.NOFOLLOW_LINKS);
        Path rootName = real.getFileName();
        if (rootName == null || !rootName.toString().endsWith("_tranquility")) {
            throw new IOException("The EVE settings directory must end in _tranquility.");
        }
        return real;
    }

    private Optional<Path> resolveTranquilityDirectory(Path selected) {
        if (selected == null) {
            return Optional.empty();
        }
        Path path = selected.toAbsolutePath().normalize();
        Path selectedName = path.getFileName();
        if (selectedName != null && selectedName.toString().startsWith("settings_")) {
            Path parent = path.getParent();
            if (parent == null) {
                return Optional.empty();
            }
            path = parent;
        }
        if (isTranquilityDirectory(path)) {
            return realPath(path);
        }
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        try (Stream<Path> children = Files.list(path)) {
            return children.filter(this::isTranquilityDirectory)
                    .max(Comparator.comparingLong(this::newestCharacterSettingsTimestamp))
                    .flatMap(EveSettingsService::realPath);
        } catch (IOException | SecurityException e) {
            return Optional.empty();
        }
    }

    private boolean isTranquilityDirectory(Path path) {
        Path filename = path == null ? null : path.getFileName();
        if (filename == null || !filename.toString().endsWith("_tranquility")
                || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            return false;
        }
        try (DirectoryStream<Path> profiles = Files.newDirectoryStream(path, "settings_*")) {
            for (Path profile : profiles) {
                if (Files.isDirectory(profile, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(profile)) {
                    return true;
                }
            }
        } catch (IOException | SecurityException ignored) {
            return false;
        }
        return false;
    }

    private long newestCharacterSettingsTimestamp(Path root) {
        long newest = 0;
        try (DirectoryStream<Path> profiles = Files.newDirectoryStream(root, "settings_*")) {
            for (Path profile : profiles) {
                if (!Files.isDirectory(profile, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                try (DirectoryStream<Path> files = Files.newDirectoryStream(profile, "core_char_*.dat")) {
                    for (Path file : files) {
                        Path filename = file.getFileName();
                        if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                                && filename != null && CHARACTER_FILE.matcher(filename.toString()).matches()) {
                            newest = Math.max(newest, Files.getLastModifiedTime(file, LinkOption.NOFOLLOW_LINKS).toMillis());
                        }
                    }
                }
            }
        } catch (IOException | SecurityException ignored) {
            return 0;
        }
        return newest;
    }

    private static void addSettingsFile(Map<Long, SettingsFile> target, long id, Path file) throws IOException {
        target.put(id, new SettingsFile(id, file.toAbsolutePath().normalize(),
                Files.getLastModifiedTime(file, LinkOption.NOFOLLOW_LINKS).toInstant(), Files.size(file)));
    }

    private static Optional<Path> realPath(Path path) {
        try {
            return Optional.of(path.toRealPath(LinkOption.NOFOLLOW_LINKS));
        } catch (IOException | SecurityException e) {
            return Optional.empty();
        }
    }

    private static Path createBackupDirectory(Path root) throws IOException {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Path directory = root.resolve(".evefarm-backups")
                .resolve(BACKUP_TIMESTAMP.format(Instant.now()) + "-" + suffix);
        return Files.createDirectories(directory);
    }

    private static void verifyIdentical(Path expected, Path actual) throws IOException {
        if (Files.size(expected) != Files.size(actual)
                || !MessageDigest.isEqual(sha256(expected), sha256(actual))) {
            throw new IOException("Copied EVE settings failed verification for " + actual.getFileName());
        }
    }

    private static byte[] sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read > 0) {
                        digest.update(buffer, 0, read);
                    }
                }
            }
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static void forceFile(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private static void replace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void rollback(List<Path> committed, Map<Path, Path> backups, IOException original) {
        for (int index = committed.size() - 1; index >= 0; index--) {
            Path target = committed.get(index);
            try {
                Path backup = backups.get(target);
                if (backup == null) {
                    Files.deleteIfExists(target);
                } else {
                    Path targetParent = target.getParent();
                    if (targetParent == null) {
                        throw new IOException("Target settings file has no parent directory: " + target);
                    }
                    Path temporary = Files.createTempFile(targetParent,
                            target.getFileName() + ".evefarm-rollback-", ".tmp");
                    try {
                        Files.copy(backup, temporary, StandardCopyOption.REPLACE_EXISTING);
                        forceFile(temporary);
                        replace(temporary, target);
                    } finally {
                        Files.deleteIfExists(temporary);
                    }
                }
            } catch (IOException rollbackFailure) {
                original.addSuppressed(rollbackFailure);
            }
        }
    }

    private static boolean detectRunningClient() {
        try (Stream<ProcessHandle> processes = ProcessHandle.allProcesses()) {
            return processes.anyMatch(process -> {
                ProcessHandle.Info info = process.info();
                String command = info.command().orElse("").replace('\\', '/').toLowerCase(Locale.ROOT);
                String commandLine = info.commandLine().orElse("").replace('\\', '/').toLowerCase(Locale.ROOT);
                return command.endsWith("/exefile.exe") || command.endsWith("/exefile")
                        || commandLine.contains("/exefile.exe");
            });
        } catch (SecurityException e) {
            return false;
        }
    }

    private record FileCopy(Path source, Path target) {
    }

    public record SettingsFile(long id, Path path, Instant modifiedAt, long size) {
    }

    public record Profile(String name, Path directory, List<SettingsFile> characterFiles,
                          List<SettingsFile> accountFiles) {
        public Profile {
            characterFiles = List.copyOf(characterFiles);
            accountFiles = List.copyOf(accountFiles);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public record ScanResult(Path settingsRoot, List<Profile> profiles) {
        public ScanResult {
            profiles = List.copyOf(profiles);
        }
    }

    public record CopyRequest(Path settingsRoot, Path sourceCharacter, Path targetCharacter,
                              Path sourceAccount, Path targetAccount) {
    }

    public record CopyResult(List<Path> copiedFiles, Optional<Path> backupDirectory) {
        public CopyResult {
            copiedFiles = List.copyOf(copiedFiles);
        }
    }
}
