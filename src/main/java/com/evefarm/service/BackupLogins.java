package com.evefarm.service;

import com.evefarm.db.Database;
import com.evefarm.db.JdbcUtil;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.db.dao.TokenDao;
import com.evefarm.util.PasswordProtection;
import com.evefarm.util.TokenCipher;
import org.sqlite.SQLiteConfig;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

final class BackupLogins {

    private static final Logger LOG = Logger.getLogger(BackupLogins.class.getName());
    static final String LOGIN = "login:";

    record Login(Map<String, Object> character, Map<String, Object> token) {
    }

    record Here(Map<Long, Login> logins, String backupPassword, String janiceKey) {
    }

    private record Saved(long characterId, String name, boolean removed, String refreshToken, String status) {
        boolean usableHere() {
            return refreshToken != null && TokenDao.ACTIVE.equals(status) && TokenCipher.canReadHere(refreshToken);
        }
    }

    private BackupLogins() {
    }

    static Here here(Database live) {
        Map<Long, Map<String, Object>> characters = new HashMap<>();
        Map<Long, Map<String, Object>> tokens = new HashMap<>();
        Map<String, String> settings = new HashMap<>();
        synchronized (live) {
            Connection connection = live.connection();
            try (PreparedStatement characterRows = connection.prepareStatement(
                    "SELECT * FROM characters WHERE removed_at IS NULL");
                 PreparedStatement tokenRows = connection.prepareStatement("SELECT * FROM tokens WHERE status = ?");
                 PreparedStatement settingRows = connection.prepareStatement(
                         "SELECT key, value FROM app_settings WHERE key IN (?, ?)")) {
                try (ResultSet rs = characterRows.executeQuery()) {
                    while (rs.next()) {
                        characters.put(rs.getLong("character_id"), row(rs));
                    }
                }
                tokenRows.setString(1, TokenDao.ACTIVE);
                try (ResultSet rs = tokenRows.executeQuery()) {
                    while (rs.next()) {
                        tokens.put(rs.getLong("character_id"), row(rs));
                    }
                }
                settingRows.setString(1, SettingsDao.BACKUP_PASSWORD);
                settingRows.setString(2, SettingsDao.JANICE_API_KEY);
                try (ResultSet rs = settingRows.executeQuery()) {
                    while (rs.next()) {
                        settings.put(rs.getString("key"), rs.getString("value"));
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Couldn't read the logins on this computer", e);
            }
        }
        Map<Long, Login> logins = new HashMap<>();
        tokens.forEach((characterId, token) -> {
            Map<String, Object> character = characters.get(characterId);
            if (character != null && TokenCipher.canReadHere((String) token.get("refresh_token"))) {
                logins.put(characterId, new Login(character, token));
            }
        });
        return new Here(logins, readable(settings.get(SettingsDao.BACKUP_PASSWORD)),
                readable(settings.get(SettingsDao.JANICE_API_KEY)));
    }

    static void addPortableCopies(Path backup, PasswordProtection protection) {
        try (Connection connection = new SQLiteConfig().createConnection("jdbc:sqlite:" + backup)) {
            Map<String, String> sealed = new LinkedHashMap<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT character_id, refresh_token FROM tokens WHERE status = ?")) {
                ps.setString(1, TokenDao.ACTIVE);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        sealed.put(LOGIN + rs.getLong("character_id"), rs.getString("refresh_token"));
                    }
                }
            }
            String janiceKey = setting(connection, SettingsDao.JANICE_API_KEY);
            if (janiceKey != null && !janiceKey.isBlank()) {
                sealed.put(SettingsDao.JANICE_API_KEY, janiceKey);
            }
            Map<String, String> portable = new LinkedHashMap<>();
            sealed.forEach((name, value) -> {
                try {
                    portable.put(name, protection.protect(TokenCipher.decrypt(value)));
                } catch (IllegalStateException e) {
                    LOG.info("Left " + name + " out of the backup's password-protected logins: " + e.getMessage());
                }
            });
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR REPLACE INTO backup_secret(name, value) VALUES (?, ?)")) {
                for (Map.Entry<String, String> entry : portable.entrySet()) {
                    ps.setString(1, entry.getKey());
                    ps.setString(2, entry.getValue());
                    ps.addBatch();
                }
                ps.executeBatch();
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Couldn't add the password-protected logins to " + backup.getFileName(), e);
        }
    }

    static BackupRestoreService.PasswordNeeds passwordNeeds(Path backup, Here here) {
        SQLiteConfig readOnly = new SQLiteConfig();
        readOnly.setReadOnly(true);
        try (Connection connection = readOnly.createConnection("jdbc:sqlite:" + backup)) {
            Map<String, String> secrets = secrets(connection);
            if (secrets.isEmpty()) {
                return new BackupRestoreService.PasswordNeeds(List.of(), false);
            }
            List<String> characters = new ArrayList<>();
            for (Saved saved : saved(connection)) {
                if (!saved.removed() && secrets.containsKey(LOGIN + saved.characterId())
                        && !here.logins().containsKey(saved.characterId()) && !saved.usableHere()) {
                    characters.add(saved.name());
                }
            }
            boolean janiceKey = secrets.containsKey(SettingsDao.JANICE_API_KEY) && here.janiceKey() == null
                    && readable(setting(connection, SettingsDao.JANICE_API_KEY)) == null;
            return new BackupRestoreService.PasswordNeeds(characters, janiceKey);
        } catch (SQLException e) {
            throw new IllegalStateException("Couldn't read the logins in the backup", e);
        }
    }

    static List<String> carryOver(Database staged, Here here, String password) {
        PasswordProtection protection = password == null || password.isEmpty() ? null
                : new PasswordProtection(password);
        Connection connection = staged.connection();
        try {
            Map<String, String> secrets = secrets(connection);
            Set<Long> inBackup = new HashSet<>();
            Map<Long, String> resealed = new LinkedHashMap<>();
            List<Long> rejected = new ArrayList<>();
            List<String> toAddAgain = new ArrayList<>();
            boolean passwordWorked = false;
            for (Saved saved : saved(connection)) {
                inBackup.add(saved.characterId());
                if (here.logins().containsKey(saved.characterId()) || saved.usableHere()) {
                    continue;
                }
                String secret = secrets.get(LOGIN + saved.characterId());
                if (protection != null && secret != null) {
                    resealed.put(saved.characterId(), TokenCipher.encrypt(protection.unprotect(secret)));
                    passwordWorked = true;
                } else if (!saved.removed()) {
                    if (saved.refreshToken() != null) {
                        rejected.add(saved.characterId());
                    }
                    toAddAgain.add(saved.name());
                }
            }

            String stagedJaniceKey = setting(connection, SettingsDao.JANICE_API_KEY);
            String janiceKey = "";
            if (here.janiceKey() != null) {
                janiceKey = here.janiceKey();
            } else if (readable(stagedJaniceKey) != null) {
                janiceKey = stagedJaniceKey;
            } else if (protection != null && secrets.containsKey(SettingsDao.JANICE_API_KEY)) {
                janiceKey = TokenCipher.encrypt(protection.unprotect(secrets.get(SettingsDao.JANICE_API_KEY)));
                passwordWorked = true;
            }

            String stagedPassword = setting(connection, SettingsDao.BACKUP_PASSWORD);
            String backupPassword = "";
            if (here.backupPassword() != null) {
                backupPassword = here.backupPassword();
            } else if (passwordWorked) {
                backupPassword = TokenCipher.encrypt(password);
            } else if (readable(stagedPassword) != null) {
                backupPassword = stagedPassword;
            }

            write(connection, here, inBackup, resealed, rejected, janiceKey, backupPassword);
            return toAddAgain;
        } catch (SQLException e) {
            throw new IllegalStateException("Couldn't move the logins into the restored data", e);
        }
    }

    private static void write(Connection connection, Here here, Set<Long> inBackup, Map<Long, String> resealed,
                              List<Long> rejected, String janiceKey, String backupPassword) throws SQLException {
        String now = Instant.now().toString();
        connection.setAutoCommit(false);
        try {
            for (Map.Entry<Long, Login> entry : here.logins().entrySet()) {
                if (inBackup.contains(entry.getKey())) {
                    try (PreparedStatement ps = connection.prepareStatement(
                            "UPDATE characters SET removed_at = NULL WHERE character_id = ?")) {
                        ps.setLong(1, entry.getKey());
                        ps.executeUpdate();
                    }
                } else {
                    insert(connection, "INSERT INTO characters", entry.getValue().character());
                }
                insert(connection, "INSERT OR REPLACE INTO tokens", entry.getValue().token());
            }
            try (PreparedStatement ps = connection.prepareStatement("""
                    UPDATE tokens SET refresh_token = ?, access_token = NULL, access_token_expires_at = NULL,
                      status = ?, updated_at = ?
                    WHERE character_id = ?
                    """)) {
                for (Map.Entry<Long, String> entry : resealed.entrySet()) {
                    ps.setString(1, entry.getValue());
                    ps.setString(2, TokenDao.ACTIVE);
                    ps.setString(3, now);
                    ps.setLong(4, entry.getKey());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE tokens SET status = ?, updated_at = ? WHERE character_id = ?")) {
                for (long characterId : rejected) {
                    ps.setString(1, TokenDao.LOGIN_REJECTED);
                    ps.setString(2, now);
                    ps.setLong(3, characterId);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            setSetting(connection, SettingsDao.JANICE_API_KEY, janiceKey);
            setSetting(connection, SettingsDao.BACKUP_PASSWORD, backupPassword);
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM backup_secret")) {
                ps.executeUpdate();
            }
            connection.commit();
        } catch (SQLException | RuntimeException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private static List<Saved> saved(Connection connection) throws SQLException {
        String sql = """
                SELECT c.character_id, c.character_name, c.removed_at, t.refresh_token, t.status
                FROM characters c LEFT JOIN tokens t ON t.character_id = c.character_id
                ORDER BY c.character_name
                """;
        List<Saved> saved = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                saved.add(new Saved(rs.getLong("character_id"), rs.getString("character_name"),
                        rs.getString("removed_at") != null, rs.getString("refresh_token"), rs.getString("status")));
            }
        }
        return saved;
    }

    private static Map<String, String> secrets(Connection connection) throws SQLException {
        Map<String, String> secrets = new HashMap<>();
        try (PreparedStatement table = connection.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'backup_secret'");
             ResultSet exists = table.executeQuery()) {
            if (!exists.next()) {
                return secrets;
            }
        }
        try (PreparedStatement ps = connection.prepareStatement("SELECT name, value FROM backup_secret");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                secrets.put(rs.getString("name"), rs.getString("value"));
            }
        }
        return secrets;
    }

    private static String setting(Connection connection, String key) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT value FROM app_settings WHERE key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("value") : null;
            }
        }
    }

    private static void setSetting(Connection connection, String key, String value) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO app_settings(key, value) VALUES (?, ?)
                ON CONFLICT(key) DO UPDATE SET value = excluded.value
                """)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }

    private static String readable(String sealed) {
        return sealed != null && !sealed.isBlank() && TokenCipher.canReadHere(sealed) ? sealed : null;
    }

    private static Map<String, Object> row(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        ResultSetMetaData meta = rs.getMetaData();
        for (int column = 1; column <= meta.getColumnCount(); column++) {
            row.put(meta.getColumnName(column), rs.getObject(column));
        }
        return row;
    }

    private static void insert(Connection connection, String into, Map<String, Object> row) throws SQLException {
        String sql = into + "(" + String.join(", ", row.keySet()) + ") VALUES ("
                + JdbcUtil.placeholders(row.size()) + ")";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int index = 1;
            for (Object value : row.values()) {
                ps.setObject(index++, value);
            }
            ps.executeUpdate();
        }
    }
}
