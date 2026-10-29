package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.model.TokenRecord;
import com.evefarm.util.TokenCipher;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

public final class TokenDao {

    private static final Logger LOG = Logger.getLogger(TokenDao.class.getName());
    public static final String ACTIVE = "active";
    public static final String LOGIN_REJECTED = "needs_reauth";

    private final Database database;

    public TokenDao(Database database) {
        this.database = database;
    }

    public void saveLogin(long characterId, String refreshToken) {
        String sql = """
                INSERT INTO tokens(character_id, refresh_token, status, updated_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(character_id) DO UPDATE SET
                  refresh_token = excluded.refresh_token,
                  access_token = NULL,
                  access_token_expires_at = NULL,
                  status = excluded.status,
                  updated_at = excluded.updated_at
                """;
        String sealed = TokenCipher.encrypt(refreshToken);
        synchronized (database) {
            Connection connection = database.connection();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, characterId);
                ps.setString(2, sealed);
                ps.setString(3, ACTIVE);
                ps.setString(4, Instant.now().toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to save the login of character " + characterId, e);
            }
        }
    }

    public boolean saveRefreshedLogin(long characterId, String refreshToken) {
        String sql = "UPDATE tokens SET refresh_token = ?, updated_at = ? WHERE character_id = ? AND status = ?";
        String sealed = TokenCipher.encrypt(refreshToken);
        synchronized (database) {
            Connection connection = database.connection();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, sealed);
                ps.setString(2, Instant.now().toString());
                ps.setLong(3, characterId);
                ps.setString(4, ACTIVE);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to save the refreshed login of character " + characterId, e);
            }
        }
    }

    public void markLoginRejected(long characterId) {
        String sql = "UPDATE tokens SET status = ?, updated_at = ? WHERE character_id = ?";
        synchronized (database) {
            Connection connection = database.connection();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, LOGIN_REJECTED);
                ps.setString(2, Instant.now().toString());
                ps.setLong(3, characterId);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to flag the login of character " + characterId, e);
            }
        }
    }

    public Optional<TokenRecord> find(long characterId) {
        String sql = "SELECT refresh_token, status FROM tokens WHERE character_id = ?";
        String sealed;
        boolean rejected;
        synchronized (database) {
            Connection connection = database.connection();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, characterId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    sealed = rs.getString("refresh_token");
                    rejected = LOGIN_REJECTED.equals(rs.getString("status"));
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to read token for character " + characterId, e);
            }
        }
        return Optional.of(rejected ? TokenRecord.rejected() : TokenRecord.active(TokenCipher.decrypt(sealed)));
    }

    public boolean hasTokens() {
        synchronized (database) {
            Connection connection = database.connection();
            try (PreparedStatement ps = connection.prepareStatement("SELECT 1 FROM tokens LIMIT 1");
                 ResultSet rs = ps.executeQuery()) {
                return rs.next();
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to check for stored tokens", e);
            }
        }
    }

    public Set<Long> characterIdsNeedingLogin() {
        String sql = """
                SELECT c.character_id, t.refresh_token, t.status
                FROM characters c LEFT JOIN tokens t ON t.character_id = c.character_id
                WHERE c.removed_at IS NULL
                """;
        Set<Long> characterIds = new HashSet<>();
        Map<Long, String> sealed = new HashMap<>();
        synchronized (database) {
            Connection connection = database.connection();
            try (PreparedStatement ps = connection.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long characterId = rs.getLong("character_id");
                    String refreshToken = rs.getString("refresh_token");
                    if (refreshToken == null || LOGIN_REJECTED.equals(rs.getString("status"))) {
                        characterIds.add(characterId);
                    } else {
                        sealed.put(characterId, refreshToken);
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to check which characters need to log in again", e);
            }
        }
        sealed.forEach((characterId, refreshToken) -> {
            if (!TokenCipher.canReadHere(refreshToken)) {
                characterIds.add(characterId);
            }
        });
        return characterIds;
    }

    public void migrateLegacyPlaintextTokens() {
        if (!TokenCipher.isProtectionAvailable()) {
            LOG.warning("Secret storage is unavailable; legacy plaintext tokens will not be loaded or migrated");
            return;
        }
        Map<Long, String> plaintext = new HashMap<>();
        synchronized (database) {
            Connection connection = database.connection();
            try (PreparedStatement ps = connection.prepareStatement("SELECT character_id, refresh_token FROM tokens");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String refreshToken = rs.getString("refresh_token");
                    if (!TokenCipher.isEncrypted(refreshToken)) {
                        plaintext.put(rs.getLong("character_id"), refreshToken);
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to scan tokens for legacy plaintext rows", e);
            }
        }
        if (plaintext.isEmpty()) {
            return;
        }

        Map<Long, String> sealed = new HashMap<>();
        plaintext.forEach((characterId, refreshToken) -> sealed.put(characterId, TokenCipher.encrypt(refreshToken)));
        String sql = """
                UPDATE tokens SET refresh_token = ?, access_token = NULL, access_token_expires_at = NULL
                WHERE character_id = ?
                """;
        database.transaction("Failed to encrypt legacy plaintext tokens", connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                for (Map.Entry<Long, String> entry : sealed.entrySet()) {
                    ps.setString(1, entry.getValue());
                    ps.setLong(2, entry.getKey());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        });
        LOG.info("Encrypted " + sealed.size() + " legacy plaintext token row(s)");
    }
}
