package com.evefarm.db.dao;

import com.evefarm.auth.TokenCipher;
import com.evefarm.db.Database;
import com.evefarm.model.TokenRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

public final class TokenDao {

    private static final Logger LOG = Logger.getLogger(TokenDao.class.getName());

    private final Database database;

    public TokenDao(Database database) {
        this.database = database;
    }

    public void upsert(long characterId, String refreshToken, String accessToken, Instant accessTokenExpiresAt) {
        String sql = """
                INSERT INTO tokens(character_id, refresh_token, access_token, access_token_expires_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(character_id) DO UPDATE SET
                  refresh_token = excluded.refresh_token,
                  access_token = excluded.access_token,
                  access_token_expires_at = excluded.access_token_expires_at,
                  updated_at = excluded.updated_at
                """;
        synchronized (database) {
            Connection connection = database.connection();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, characterId);
                ps.setString(2, TokenCipher.encrypt(refreshToken));
                ps.setString(3, TokenCipher.encrypt(accessToken));
                ps.setString(4, accessTokenExpiresAt == null ? null : accessTokenExpiresAt.toString());
                ps.setString(5, Instant.now().toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to upsert token for character " + characterId, e);
            }
        }
    }

    public Optional<TokenRecord> find(long characterId) {
        String sql = "SELECT * FROM tokens WHERE character_id = ?";
        synchronized (database) {
            Connection connection = database.connection();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, characterId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    String expiresAtRaw = rs.getString("access_token_expires_at");
                    return Optional.of(new TokenRecord(
                            rs.getLong("character_id"),
                            TokenCipher.decrypt(rs.getString("refresh_token")),
                            TokenCipher.decrypt(rs.getString("access_token")),
                            expiresAtRaw == null ? null : Instant.parse(expiresAtRaw)
                    ));
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to read token for character " + characterId, e);
            }
        }
    }

    public void migrateLegacyPlaintextTokens() {
        if (!TokenCipher.isProtectionAvailable()) {
            LOG.warning("Windows DPAPI is unavailable; legacy plaintext tokens will not be loaded or migrated");
            return;
        }
        String selectSql = "SELECT character_id, refresh_token, access_token, access_token_expires_at FROM tokens";
        record LegacyRow(long characterId, String refreshToken, String accessToken, String expiresAt) {
        }
        List<LegacyRow> legacyRows = new ArrayList<>();
        synchronized (database) {
            Connection connection = database.connection();
            try (PreparedStatement ps = connection.prepareStatement(selectSql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String refreshToken = rs.getString("refresh_token");
                    String accessToken = rs.getString("access_token");
                    if (!TokenCipher.isEncrypted(refreshToken)) {
                        legacyRows.add(new LegacyRow(
                                rs.getLong("character_id"), refreshToken, accessToken,
                                rs.getString("access_token_expires_at")));
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to scan tokens for legacy plaintext rows", e);
            }
        }

        for (LegacyRow row : legacyRows) {
            Instant expiresAt = row.expiresAt() == null ? null : Instant.parse(row.expiresAt());
            upsert(row.characterId(), row.refreshToken(), row.accessToken(), expiresAt);
        }
        if (!legacyRows.isEmpty()) {
            LOG.info("Encrypted " + legacyRows.size() + " legacy plaintext token row(s) with DPAPI");
        }
    }
}
