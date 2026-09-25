package com.evefarm.service;

import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.service.EveSettingsService.CopyRequest;
import com.evefarm.service.EveSettingsService.CopyResult;
import com.evefarm.service.EveSettingsService.ScanResult;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EveSettingsServiceTest {

    private SettingsDao settings;

    @BeforeEach
    void setUp() {
        Database database = new Database(":memory:");
        MigrationRunner.run(database);
        settings = new SettingsDao(database);
    }

    @Test
    void scansProfilesAndExtractsCharacterAndAccountIds(@TempDir Path directory) throws Exception {
        Path root = tranquility(directory);
        Path defaultProfile = Files.createDirectories(root.resolve("settings_Default"));
        Files.writeString(defaultProfile.resolve("core_char_90000001.dat"), "character");
        Files.writeString(defaultProfile.resolve("core_user_123456.dat"), "account");
        Files.writeString(defaultProfile.resolve("core_char_90000001_rift_backup_old.dat"), "ignored");
        Files.writeString(defaultProfile.resolve("notes.dat"), "ignored");

        ScanResult result = service(false).scan(directory);

        assertEquals(root.toRealPath(), result.settingsRoot());
        assertEquals(1, result.profiles().size());
        assertEquals("Default", result.profiles().getFirst().name());
        assertEquals(90000001L, result.profiles().getFirst().characterFiles().getFirst().id());
        assertEquals(123456L, result.profiles().getFirst().accountFiles().getFirst().id());
    }

    @Test
    void storesTheResolvedTranquilityDirectory(@TempDir Path directory) throws Exception {
        Path root = tranquility(directory);
        Files.createDirectories(root.resolve("settings_Default"));

        Path selected = service(false).setSettingsDirectory(directory);

        assertEquals(root.toRealPath(), selected);
        assertEquals(root.toRealPath().toString(), settings.get(SettingsDao.EVE_SETTINGS_DIRECTORY).orElseThrow());
    }

    @Test
    void copiesCharacterAndAccountAndBacksUpExistingTargets(@TempDir Path directory) throws Exception {
        Path root = tranquility(directory);
        Path profile = Files.createDirectories(root.resolve("settings_Default"));
        Path sourceCharacter = write(profile, "core_char_1.dat", "source character");
        Path targetCharacter = write(profile, "core_char_2.dat", "old character");
        Path sourceAccount = write(profile, "core_user_10.dat", "source account");
        Path targetAccount = write(profile, "core_user_20.dat", "old account");

        CopyResult result = service(false).copy(new CopyRequest(root, sourceCharacter, targetCharacter,
                sourceAccount, targetAccount));

        assertEquals("source character", Files.readString(targetCharacter));
        assertEquals("source account", Files.readString(targetAccount));
        assertEquals(2, result.copiedFiles().size());
        Path backup = result.backupDirectory().orElseThrow();
        assertEquals("old character", Files.readString(backup.resolve("settings_Default/core_char_2.dat")));
        assertEquals("old account", Files.readString(backup.resolve("settings_Default/core_user_20.dat")));
    }

    @Test
    void backsUpNextToTheRealFilesWhenTheFolderIsReachedThroughALink(@TempDir Path directory) throws Exception {
        Path real = Files.createDirectories(directory.resolve("real"));
        Path link;
        try {
            link = Files.createSymbolicLink(directory.resolve("link"), real);
        } catch (IOException | UnsupportedOperationException e) {
            Assumptions.abort("symbolic links are not available here: " + e.getMessage());
            return;
        }
        Path root = tranquility(link);
        Path profile = Files.createDirectories(root.resolve("settings_Default"));
        Path source = write(profile, "core_char_1.dat", "source character");
        Path target = write(profile, "core_char_2.dat", "old character");

        CopyResult result = service(false).copy(new CopyRequest(root, source, target, null, null));

        assertEquals("source character", Files.readString(target));
        Path backup = result.backupDirectory().orElseThrow();
        assertEquals("old character", Files.readString(backup.resolve("settings_Default/core_char_2.dat")));
    }

    @Test
    void createsANewTargetWithoutPretendingABackupExists(@TempDir Path directory) throws Exception {
        Path root = tranquility(directory);
        Path profile = Files.createDirectories(root.resolve("settings_Default"));
        Path source = write(profile, "core_char_1.dat", "source");
        Path target = profile.resolve("core_char_2.dat");

        CopyResult result = service(false).copy(new CopyRequest(root, source, target, null, null));

        assertEquals("source", Files.readString(target));
        assertTrue(result.backupDirectory().isEmpty());
    }

    @Test
    void validatesEverySourceBeforeChangingAnyTarget(@TempDir Path directory) throws Exception {
        Path root = tranquility(directory);
        Path profile = Files.createDirectories(root.resolve("settings_Default"));
        Path sourceCharacter = write(profile, "core_char_1.dat", "source character");
        Path targetCharacter = write(profile, "core_char_2.dat", "old character");
        Path missingAccount = profile.resolve("core_user_10.dat");
        Path targetAccount = write(profile, "core_user_20.dat", "old account");

        assertThrows(IOException.class, () -> service(false).copy(new CopyRequest(root, sourceCharacter,
                targetCharacter, missingAccount, targetAccount)));

        assertEquals("old character", Files.readString(targetCharacter));
        assertEquals("old account", Files.readString(targetAccount));
    }

    @Test
    void refusesToCopyWhileEveIsRunning(@TempDir Path directory) throws Exception {
        Path root = tranquility(directory);
        Path profile = Files.createDirectories(root.resolve("settings_Default"));
        Path source = write(profile, "core_char_1.dat", "source");
        Path target = write(profile, "core_char_2.dat", "target");

        IOException error = assertThrows(IOException.class,
                () -> service(true).copy(new CopyRequest(root, source, target, null, null)));

        assertTrue(error.getMessage().contains("running"));
        assertEquals("target", Files.readString(target));
    }

    @Test
    void refusesTargetsOutsideTheSelectedProfile(@TempDir Path directory) throws Exception {
        Path root = tranquility(directory);
        Path profile = Files.createDirectories(root.resolve("settings_Default"));
        Path source = write(profile, "core_char_1.dat", "source");
        Path outside = Files.createDirectories(directory.resolve("settings_Other")).resolve("core_char_2.dat");

        assertThrows(IOException.class,
                () -> service(false).copy(new CopyRequest(root, source, outside, null, null)));
        assertFalse(Files.exists(outside));
    }

    private EveSettingsService service(boolean running) {
        return new EveSettingsService(settings, () -> running);
    }

    private static Path tranquility(Path directory) throws IOException {
        return Files.createDirectories(directory.resolve("c_ccp_eve_tq_tranquility"));
    }

    private static Path write(Path directory, String filename, String contents) throws IOException {
        Path file = directory.resolve(filename);
        Files.writeString(file, contents);
        return file;
    }
}
