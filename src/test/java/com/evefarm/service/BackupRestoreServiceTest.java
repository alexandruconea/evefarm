package com.evefarm.service;

import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.db.dao.SettingsDao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void onlyAutomaticBackupFilesAreListedForPruning(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("evefarm-auto-2026-09-23_1200.db"), "");
        Files.writeString(directory.resolve("evefarm-backup-20260923-120000.db"), "");
        Files.writeString(directory.resolve("evefarm-auto-not-a-date.db"), "");
        Files.writeString(directory.resolve("notes.txt"), "");

        List<BackupRestoreService.AutoBackup> listed = BackupRestoreService.listAutoBackups(directory);

        assertEquals(1, listed.size());
        assertEquals(LocalDateTime.of(2026, 9, 23, 12, 0), listed.get(0).takenAt());
    }
}
