package com.evefarm.service;

import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.util.AppPaths;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BackupRestoreService {

    private static final Logger LOG = Logger.getLogger(BackupRestoreService.class.getName());
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    static final Duration AUTO_BACKUP_INTERVAL = Duration.ofHours(20);
    static final int KEEP_DAILY = 7;
    static final int KEEP_MONTHLY = 12;
    private static final String AUTO_BACKUP_PREFIX = "evefarm-auto-";
    private static final DateTimeFormatter AUTO_BACKUP_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm");
    private static final Pattern AUTO_BACKUP_NAME =
            Pattern.compile("^evefarm-auto-(\\d{4}-\\d{2}-\\d{2}_\\d{4})\\.db$");

    private final Database database;
    private final SettingsDao settingsDao;

    public BackupRestoreService(Database database, SettingsDao settingsDao) {
        this.database = database;
        this.settingsDao = settingsDao;
    }

    public static String defaultBackupFileName() {
        return "evefarm-backup-" + LocalDateTime.now().format(TIMESTAMP) + ".db";
    }

    public void backupTo(Path destination) throws IOException {
        writeSnapshot(destination);
    }

    private void writeSnapshot(Path destination) throws IOException {
        Path target = destination.toAbsolutePath().normalize();
        ensureSafeBackupTarget(target, AppPaths.databaseFile(), AppPaths.pendingRestoreFile());
        Files.createDirectories(target.getParent());
        Path partial = target.resolveSibling("." + target.getFileName() + "." + UUID.randomUUID() + ".partial");
        try {
            synchronized (database) {
                try (Statement statement = database.connection().createStatement()) {
                    statement.execute("VACUUM INTO '" + partial.toString().replace("'", "''") + "'");
                } catch (SQLException e) {
                    throw new IOException("Failed to write the backup to " + target, e);
                }
            }
            moveReplacing(partial, target);
        } finally {
            Files.deleteIfExists(partial);
        }
    }

    static void moveReplacing(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static void ensureSafeBackupTarget(Path target, Path liveDatabase, Path pendingRestore) throws IOException {
        Path normalizedTarget = target.toAbsolutePath().normalize();
        Path normalizedLive = liveDatabase.toAbsolutePath().normalize();
        Path normalizedPending = pendingRestore.toAbsolutePath().normalize();
        List<Path> reserved = List.of(
                normalizedLive,
                normalizedLive.resolveSibling(normalizedLive.getFileName() + "-wal"),
                normalizedLive.resolveSibling(normalizedLive.getFileName() + "-shm"),
                normalizedPending);
        for (Path path : reserved) {
            if (normalizedTarget.equals(path)
                    || (Files.exists(normalizedTarget) && Files.exists(path)
                    && Files.isSameFile(normalizedTarget, path))) {
                throw new IOException("Refusing to overwrite an EVE Farm working database file: " + target);
            }
        }
    }

    record AutoBackup(Path file, LocalDateTime takenAt) {
    }

    public Optional<Path> autoBackupIfDue() {
        try {
            Path directory = AppPaths.backupDir();
            Optional<LocalDateTime> newest = listAutoBackups(directory).stream()
                    .map(AutoBackup::takenAt).max(Comparator.naturalOrder());
            LocalDateTime now = LocalDateTime.now();
            if (newest.isPresent() && newest.get().plus(AUTO_BACKUP_INTERVAL).isAfter(now)) {
                return Optional.empty();
            }
            Path file = directory.resolve(AUTO_BACKUP_PREFIX + now.format(AUTO_BACKUP_STAMP) + ".db");
            writeSnapshot(file);
            prune(directory);
            copyToExtraFolder(file);
            LOG.info("Automatic backup written to " + file);
            return Optional.of(file);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Automatic backup failed", e);
            return Optional.empty();
        }
    }

    private void copyToExtraFolder(Path file) {
        String extra = settingsDao.get(SettingsDao.BACKUP_COPY_DIRECTORY).orElse("");
        if (extra.isBlank()) {
            return;
        }
        try {
            Path directory = Paths.get(extra);
            Files.createDirectories(directory);
            Path partial = directory.resolve(file.getFileName() + ".partial");
            Files.copy(file, partial, StandardCopyOption.REPLACE_EXISTING);
            moveReplacing(partial, directory.resolve(file.getFileName()));
            prune(directory);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Couldn't copy the automatic backup to " + extra, e);
        }
    }

    private static void prune(Path directory) throws IOException {
        for (Path file : backupsToDelete(listAutoBackups(directory))) {
            Files.deleteIfExists(file);
        }
    }

    static List<AutoBackup> listAutoBackups(Path directory) throws IOException {
        List<AutoBackup> backups = new ArrayList<>();
        if (!Files.isDirectory(directory)) {
            return backups;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, AUTO_BACKUP_PREFIX + "*.db")) {
            for (Path file : files) {
                Matcher name = AUTO_BACKUP_NAME.matcher(file.getFileName().toString());
                if (name.matches()) {
                    backups.add(new AutoBackup(file, LocalDateTime.parse(name.group(1), AUTO_BACKUP_STAMP)));
                }
            }
        }
        return backups;
    }

    static List<Path> backupsToDelete(List<AutoBackup> backups) {
        List<AutoBackup> newestFirst = new ArrayList<>(backups);
        newestFirst.sort(Comparator.comparing(AutoBackup::takenAt).reversed());
        Set<Path> keep = new HashSet<>();
        for (int i = 0; i < Math.min(KEEP_DAILY, newestFirst.size()); i++) {
            keep.add(newestFirst.get(i).file());
        }
        Map<YearMonth, Path> newestPerMonth = new LinkedHashMap<>();
        for (AutoBackup backup : newestFirst) {
            newestPerMonth.putIfAbsent(YearMonth.from(backup.takenAt()), backup.file());
        }
        newestPerMonth.values().stream().limit(KEEP_MONTHLY).forEach(keep::add);
        List<Path> delete = new ArrayList<>();
        for (AutoBackup backup : newestFirst) {
            if (!keep.contains(backup.file())) {
                delete.add(backup.file());
            }
        }
        return delete;
    }

    public String validateBackupFile(Path file) {
        if (!Files.isRegularFile(file)) {
            return "Not a file.";
        }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
             Statement statement = connection.createStatement()) {
            try (ResultSet rs = statement.executeQuery("PRAGMA integrity_check")) {
                if (!rs.next() || !"ok".equalsIgnoreCase(rs.getString(1))) {
                    return "The file failed SQLite's integrity check - it may be corrupt.";
                }
            }
            try (ResultSet rs = statement.executeQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='schema_version'")) {
                if (!rs.next()) {
                    return "This doesn't look like an EVE Farm database (no schema_version table).";
                }
            }
            try (ResultSet rs = statement.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_version")) {
                if (rs.next() && rs.getInt(1) > MigrationRunner.latestVersion()) {
                    return "This backup was made by a newer version of EVE Farm. Update EVE Farm first, "
                            + "then restore it.";
                }
            }
        } catch (SQLException e) {
            return "Failed to open the file as a SQLite database: " + e.getMessage();
        }
        return checkUpgradedCopy(file);
    }

    static String checkUpgradedCopy(Path file) {
        Path copy = null;
        try {
            copy = Files.createTempFile("evefarm-restore-check-", ".db");
            Files.copy(file, copy, StandardCopyOption.REPLACE_EXISTING);
            Database restored = new Database(copy.toString());
            Database reference = new Database(":memory:");
            try {
                MigrationRunner.run(restored);
                MigrationRunner.run(reference);
                String missing = missingSchema(reference.connection(), restored.connection());
                return missing == null ? null : "The backup is incomplete: " + missing + ".";
            } finally {
                restored.close();
                reference.close();
            }
        } catch (Exception e) {
            return "The backup can't be upgraded to this version of EVE Farm: " + e.getMessage();
        } finally {
            if (copy != null) {
                deleteIfExists(copy);
                deleteIfExists(copy.resolveSibling(copy.getFileName() + "-wal"));
                deleteIfExists(copy.resolveSibling(copy.getFileName() + "-shm"));
            }
        }
    }

    static String missingSchema(Connection expected, Connection actual) throws SQLException {
        Map<String, Set<String>> actualTables = tables(actual);
        for (Map.Entry<String, Set<String>> table : tables(expected).entrySet()) {
            Set<String> columns = actualTables.get(table.getKey());
            if (columns == null) {
                return "table " + table.getKey() + " is missing";
            }
            for (String column : table.getValue()) {
                if (!columns.contains(column)) {
                    return "column " + table.getKey() + "." + column + " is missing";
                }
            }
        }
        return null;
    }

    private static Map<String, Set<String>> tables(Connection connection) throws SQLException {
        Map<String, Set<String>> tables = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")) {
            while (rs.next()) {
                tables.put(rs.getString(1), new HashSet<>());
            }
        }
        for (Map.Entry<String, Set<String>> table : tables.entrySet()) {
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery(
                         "PRAGMA table_info('" + table.getKey().replace("'", "''") + "')")) {
                while (rs.next()) {
                    table.getValue().add(rs.getString("name"));
                }
            }
        }
        return tables;
    }

    public void stageRestore(Path sourceBackupFile) throws IOException {
        Files.copy(sourceBackupFile, AppPaths.pendingRestoreFile(), StandardCopyOption.REPLACE_EXISTING);
    }

    public static Optional<Path> applyPendingRestoreIfAny() {
        Path pending = AppPaths.pendingRestoreFile();
        if (!Files.exists(pending)) {
            return Optional.empty();
        }
        Path live = AppPaths.databaseFile();
        Path liveWal = live.resolveSibling(live.getFileName() + "-wal");
        Path liveShm = live.resolveSibling(live.getFileName() + "-shm");
        try {
            Path safetyBackup = null;
            if (Files.exists(live)) {
                String suffix = LocalDateTime.now().format(TIMESTAMP);
                safetyBackup = live.resolveSibling("evefarm-before-restore-" + suffix + ".db");
                Files.copy(live, safetyBackup, StandardCopyOption.REPLACE_EXISTING);
                copyIfExists(liveWal, live.resolveSibling("evefarm-before-restore-" + suffix + ".db-wal"));
                copyIfExists(liveShm, live.resolveSibling("evefarm-before-restore-" + suffix + ".db-shm"));
                LOG.info("Backed up pre-restore database to " + safetyBackup);
            }
            Files.move(pending, live, StandardCopyOption.REPLACE_EXISTING);
            deleteIfExists(liveWal);
            deleteIfExists(liveShm);
            LOG.info("Applied staged database restore from " + pending);
            return Optional.ofNullable(safetyBackup);
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Failed to apply staged database restore - keeping the previous database", e);
            deleteIfExists(pending);
            return Optional.empty();
        }
    }

    public static void undoRestore(Path databaseBeforeRestore) throws IOException {
        putBack(databaseBeforeRestore, AppPaths.databaseFile());
        LOG.warning("The restored database couldn't be opened - put back " + databaseBeforeRestore);
    }

    static void putBack(Path previous, Path live) throws IOException {
        Files.copy(previous, live, StandardCopyOption.REPLACE_EXISTING);
        for (String suffix : List.of("-wal", "-shm")) {
            Path saved = previous.resolveSibling(previous.getFileName() + suffix);
            Path current = live.resolveSibling(live.getFileName() + suffix);
            if (Files.exists(saved)) {
                Files.copy(saved, current, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.deleteIfExists(current);
            }
        }
    }

    private static void copyIfExists(Path source, Path destination) throws IOException {
        if (Files.exists(source)) {
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteIfExists(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }
}
