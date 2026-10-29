package com.evefarm.service;

import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.db.dao.CharacterDao;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.db.dao.TokenDao;
import com.evefarm.util.ForeignSecretException;
import com.evefarm.util.TokenCipher;
import com.evefarm.util.WrongPasswordException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

class BackupLoginsTest {

    private static final String PASSWORD = "correct horse battery";
    private static final long MALPAIS = 2124165849L;
    private static final long YASONAR = 2124165850L;
    private static final long ICHIRO = 2124165851L;

    @TempDir
    Path directory;

    private MockedStatic<TokenCipher> cipher;
    private String computer = "old-pc:";
    private Path backup;
    private Path pending;

    @BeforeEach
    void setUp() {
        cipher = mockStatic(TokenCipher.class);
        cipher.when(() -> TokenCipher.encrypt(anyString())).thenAnswer(call -> computer + call.getArgument(0));
        cipher.when(() -> TokenCipher.decrypt(anyString())).thenAnswer(call -> {
            String stored = call.getArgument(0);
            if (!stored.startsWith(computer)) {
                throw new ForeignSecretException("sealed on another computer");
            }
            return stored.substring(computer.length());
        });
        cipher.when(() -> TokenCipher.canReadHere(anyString()))
                .thenAnswer(call -> call.<String>getArgument(0).startsWith(computer));
        backup = directory.resolve("evefarm-backup.db");
        pending = directory.resolve("pending-restore.db");
    }

    @AfterEach
    void tearDown() {
        cipher.close();
    }

    private static Database computerWith(Map<Long, String> logins) {
        Database database = new Database(":memory:");
        MigrationRunner.run(database);
        CharacterDao characters = new CharacterDao(database);
        TokenDao tokens = new TokenDao(database);
        logins.forEach((characterId, name) -> {
            characters.upsert(characterId, name, List.of(), null);
            tokens.saveLogin(characterId, "refresh-" + characterId);
        });
        return database;
    }

    private static BackupRestoreService service(Database database) {
        return new BackupRestoreService(database, new SettingsDao(database));
    }

    private void backUpOnTheOldComputer(boolean withPassword) throws Exception {
        Database old = computerWith(Map.of(MALPAIS, "Malpais Legate", YASONAR, "Yasonar Anoh"));
        SettingsDao settings = new SettingsDao(old);
        settings.set(SettingsDao.JANICE_API_KEY, TokenCipher.encrypt("janice-key"));
        if (withPassword) {
            settings.set(SettingsDao.BACKUP_PASSWORD, TokenCipher.encrypt(PASSWORD));
        }
        service(old).backupTo(backup);
        old.close();
        computer = "new-pc:";
    }

    private static Map<Long, String> logins(Path file) throws SQLException {
        Map<Long, String> logins = new HashMap<>();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT character_id, status, refresh_token FROM tokens");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                logins.put(rs.getLong("character_id"), rs.getString("status") + " " + rs.getString("refresh_token"));
            }
        }
        return logins;
    }

    private static String setting(Path file, String key) throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
             PreparedStatement ps = connection.prepareStatement("SELECT value FROM app_settings WHERE key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("value") : null;
            }
        }
    }

    private static int portableLogins(Path file) throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
             PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM backup_secret");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    @Test
    void thePasswordBringsTheLoginsToAnotherComputerWhichKeepsItsOwn() throws Exception {
        backUpOnTheOldComputer(true);
        BackupRestoreService newComputer = service(computerWith(Map.of(ICHIRO, "Ichiro Saihara")));

        assertEquals(new BackupRestoreService.PasswordNeeds(List.of("Malpais Legate", "Yasonar Anoh"), true),
                newComputer.passwordNeeds(backup));
        assertEquals(List.of(), newComputer.stage(backup, pending, PASSWORD));

        assertEquals(Map.of(MALPAIS, "active new-pc:refresh-" + MALPAIS, YASONAR, "active new-pc:refresh-" + YASONAR,
                ICHIRO, "active new-pc:refresh-" + ICHIRO), logins(pending));
        assertEquals("new-pc:janice-key", setting(pending, SettingsDao.JANICE_API_KEY));
        assertEquals("new-pc:" + PASSWORD, setting(pending, SettingsDao.BACKUP_PASSWORD));
        assertEquals(0, portableLogins(pending), "the password-protected copies stay in the backup only");
    }

    @Test
    void aWrongPasswordStagesNothing() throws Exception {
        backUpOnTheOldComputer(true);
        BackupRestoreService newComputer = service(computerWith(Map.of()));

        assertThrows(WrongPasswordException.class, () -> newComputer.stage(backup, pending, "wrong horse battery"));

        assertEquals(Set.of("evefarm-backup.db"), Set.of(directory.toFile().list()));
    }

    @Test
    void withoutThePasswordOnlyCharactersMissingHereMustBeAddedAgain() throws Exception {
        backUpOnTheOldComputer(true);
        BackupRestoreService newComputer = service(computerWith(Map.of(YASONAR, "Yasonar Anoh")));

        assertEquals(List.of("Malpais Legate"), newComputer.stage(backup, pending, null));

        assertEquals(Map.of(MALPAIS, TokenDao.LOGIN_REJECTED + " old-pc:refresh-" + MALPAIS,
                YASONAR, "active new-pc:refresh-" + YASONAR), logins(pending));
        assertEquals("", setting(pending, SettingsDao.JANICE_API_KEY));
        assertEquals("", setting(pending, SettingsDao.BACKUP_PASSWORD));
    }

    @Test
    void aBackupRestoredOnTheSameComputerNeedsNoPassword() throws Exception {
        Database same = computerWith(Map.of(MALPAIS, "Malpais Legate"));
        SettingsDao settings = new SettingsDao(same);
        settings.set(SettingsDao.BACKUP_PASSWORD, TokenCipher.encrypt(PASSWORD));
        BackupRestoreService sameComputer = new BackupRestoreService(same, settings);
        sameComputer.backupTo(backup);
        new CharacterDao(same).remove(MALPAIS);

        assertFalse(sameComputer.passwordNeeds(backup).any());
        assertEquals(List.of(), sameComputer.stage(backup, pending, null));

        assertEquals(Map.of(MALPAIS, "active old-pc:refresh-" + MALPAIS), logins(pending));
        assertEquals("old-pc:" + PASSWORD, setting(pending, SettingsDao.BACKUP_PASSWORD));
    }

    @Test
    void withoutABackupPasswordTheBackupKeepsNoPortableLogins() throws Exception {
        backUpOnTheOldComputer(false);

        assertEquals(0, portableLogins(backup));
        assertFalse(service(computerWith(Map.of())).passwordNeeds(backup).any());
    }
}
