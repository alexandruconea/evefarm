package com.evefarm.service;

import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.db.dao.SettingsDao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackupRestoreServiceTest {

    @Test
    void retentionKeepsTheLastWeekPlusOnePerMonthForAYear() {
        List<BackupRestoreService.AutoBackup> backups = new ArrayList<>();
        LocalDate newest = LocalDate.of(2026, 9, 23);
        for (int daysBack = 0; daysBack < 730; daysBack++) {
            LocalDate day = newest.minusDays(daysBack);
            backups.add(new BackupRestoreService.AutoBackup(Path.of("b-" + day + ".db"), day.atTime(12, 0)));
        }

        Set<Path> kept = new HashSet<>(backups.stream().map(BackupRestoreService.AutoBackup::file).toList());
        BackupRestoreService.backupsToDelete(backups).forEach(kept::remove);

        assertEquals(7 + 11, kept.size());
        assertTrue(kept.contains(Path.of("b-2026-09-17.db")));
        assertFalse(kept.contains(Path.of("b-2026-09-16.db")));
        assertTrue(kept.contains(Path.of("b-2026-08-31.db")));
        assertTrue(kept.contains(Path.of("b-2025-10-31.db")));
        assertFalse(kept.contains(Path.of("b-2025-09-30.db")), "a 13th month is one too many");
    }

    @Test
    void aBackupIsARestorableCopyEvenWhenWrittenOverAnOlderOne(@TempDir Path directory) throws Exception {
        Database database = new Database(":memory:");
        MigrationRunner.run(database);
        SettingsDao settings = new SettingsDao(database);
        settings.set(SettingsDao.LAF_THEME, "flatlaf-dark");
        BackupRestoreService service = new BackupRestoreService(database, settings);
        Path file = directory.resolve("evefarm-backup.db");

        service.backupTo(file);
        service.backupTo(file);

        assertNull(service.validateBackupFile(file), "the copy passes the same check Restore Data uses");
    }

    @Test
    void aBackupFromANewerVersionOfTheAppIsRefused(@TempDir Path directory) throws Exception {
        Database database = new Database(":memory:");
        MigrationRunner.run(database);
        try (Statement statement = database.connection().createStatement()) {
            statement.execute("INSERT INTO schema_version(version, applied_at) VALUES ("
                    + (MigrationRunner.latestVersion() + 1) + ", '2027-01-01')");
        }
        BackupRestoreService service = new BackupRestoreService(database, new SettingsDao(database));
        Path file = directory.resolve("from-the-future.db");
        service.backupTo(file);

        String error = service.validateBackupFile(file);

        assertTrue(error != null && error.contains("newer version"), error);
    }

    @Test
    void aDatabaseThatClaimsToBeUpToDateButLacksTablesIsRefused(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("hollow.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE schema_version(version INTEGER PRIMARY KEY, applied_at TEXT NOT NULL, "
                    + "checksum TEXT)");
            for (int version = 1; version <= MigrationRunner.latestVersion(); version++) {
                statement.execute("INSERT INTO schema_version(version, applied_at) VALUES (" + version + ", 'x')");
            }
        }
        Database database = new Database(":memory:");
        MigrationRunner.run(database);

        String error = new BackupRestoreService(database, new SettingsDao(database)).validateBackupFile(file);

        assertTrue(error != null && error.contains("is missing"), error);
    }

    @Test
    void puttingThePreviousDatabaseBackAlsoRestoresItsJournal(@TempDir Path directory) throws Exception {
        Path previous = directory.resolve("evefarm-before-restore.db");
        Path live = directory.resolve("evefarm.db");
        Files.writeString(previous, "previous");
        Files.writeString(directory.resolve("evefarm-before-restore.db-wal"), "previous wal");
        Files.writeString(live, "broken restore");
        Files.writeString(directory.resolve("evefarm.db-shm"), "stale shm");

        BackupRestoreService.putBack(previous, live);

        assertEquals("previous", Files.readString(live));
        assertEquals("previous wal", Files.readString(directory.resolve("evefarm.db-wal")));
        assertFalse(Files.exists(directory.resolve("evefarm.db-shm")));
    }

    @Test
    void onlyAutomaticBackupFilesAreListedForPruning(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("evefarm-auto-2026-09-23_1200.db"), "");
        Files.writeString(directory.resolve("evefarm-backup-20260923-120000.db"), "");
        Files.writeString(directory.resolve("evefarm-auto-not-a-date.db"), "");
        Files.writeString(directory.resolve("notes.txt"), "");

        List<BackupRestoreService.AutoBackup> listed = BackupRestoreService.listAutoBackups(directory);

        assertEquals(1, listed.size());
        assertEquals(LocalDateTime.of(2026, 9, 23, 12, 0), listed.get(0).takenAt());
    }

    @Test
    void workingDatabaseFilesCannotBeUsedAsBackupDestinations(@TempDir Path directory) throws Exception {
        Path live = directory.resolve("evefarm.db");
        Path pending = directory.resolve("pending-restore.db");

        assertThrows(java.io.IOException.class,
                () -> BackupRestoreService.ensureSafeBackupTarget(live, live, pending));
        assertThrows(java.io.IOException.class,
                () -> BackupRestoreService.ensureSafeBackupTarget(
                        directory.resolve("evefarm.db-wal"), live, pending));
        assertThrows(java.io.IOException.class,
                () -> BackupRestoreService.ensureSafeBackupTarget(pending, live, pending));
    }
}
