package com.evefarm.service;

import com.evefarm.db.Database;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.util.AppPaths;

import java.io.IOException;
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
        Path target = destination.toAbsolutePath();
        Files.createDirectories(target.getParent());
        Files.deleteIfExists(target);
        synchronized (database) {
            try (Statement statement = database.connection().createStatement()) {
                statement.execute("VACUUM INTO '" + target.toString().replace("'", "''") + "'");
            } catch (SQLException e) {
                throw new IOException("Failed to write the backup to " + target, e);
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
            Path partial = directory.resolve(file.getFileName() + ".partial");
            writeSnapshot(partial);
            Files.move(partial, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
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
            Files.move(partial, directory.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
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
        } catch (SQLException e) {
            return "Failed to open the file as a SQLite database: " + e.getMessage();
        }
        return null;
    }

    public void stageRestore(Path sourceBackupFile) throws IOException {
        Files.copy(sourceBackupFile, AppPaths.pendingRestoreFile(), StandardCopyOption.REPLACE_EXISTING);
    }

    public static void applyPendingRestoreIfAny() {
        Path pending = AppPaths.pendingRestoreFile();
        if (!Files.exists(pending)) {
            return;
        }
        Path live = AppPaths.databaseFile();
        Path liveWal = live.resolveSibling(live.getFileName() + "-wal");
        Path liveShm = live.resolveSibling(live.getFileName() + "-shm");
        try {
            if (Files.exists(live)) {
                String suffix = LocalDateTime.now().format(TIMESTAMP);
                Path safetyBackup = live.resolveSibling("evefarm-before-restore-" + suffix + ".db");
                Files.copy(live, safetyBackup, StandardCopyOption.REPLACE_EXISTING);
                copyIfExists(liveWal, live.resolveSibling("evefarm-before-restore-" + suffix + ".db-wal"));
                copyIfExists(liveShm, live.resolveSibling("evefarm-before-restore-" + suffix + ".db-shm"));
                LOG.info("Backed up pre-restore database to " + safetyBackup);
            }
            Files.move(pending, live, StandardCopyOption.REPLACE_EXISTING);
            deleteIfExists(liveWal);
            deleteIfExists(liveShm);
            LOG.info("Applied staged database restore from " + pending);
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Failed to apply staged database restore - keeping the previous database", e);
            deleteIfExists(pending);
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
