package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.model.EveCharacter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class CharacterDao {

    private final Database database;

    public CharacterDao(Database database) {
        this.database = database;
    }

    public void upsert(long characterId, String characterName, Long corporationId, List<String> scopes,
                        String ownerHash) {
        String sql = """
                INSERT INTO characters(character_id, character_name, corporation_id, scopes, added_at, enabled, owner_hash)
                VALUES (?, ?, ?, ?, ?, 1, ?)
                ON CONFLICT(character_id) DO UPDATE SET
                  character_name = excluded.character_name,
                  corporation_id = excluded.corporation_id,
                  scopes = excluded.scopes,
                  owner_hash = excluded.owner_hash,
                  removed_at = NULL
                """;
        synchronized (database) {
            try (PreparedStatement ps = database.connection().prepareStatement(sql)) {
                ps.setLong(1, characterId);
                ps.setString(2, characterName);
                if (corporationId != null) {
                    ps.setLong(3, corporationId);
                } else {
                    ps.setNull(3, java.sql.Types.INTEGER);
                }
                ps.setString(4, String.join(" ", scopes));
                ps.setString(5, Instant.now().toString());
                ps.setString(6, ownerHash);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to upsert character " + characterId, e);
            }
        }
    }

    public java.util.Optional<String> findOwnerHash(long characterId) {
        String sql = "SELECT owner_hash FROM characters WHERE character_id = ? AND removed_at IS NULL";
        synchronized (database) {
            try (PreparedStatement ps = database.connection().prepareStatement(sql)) {
                ps.setLong(1, characterId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return java.util.Optional.empty();
                    }
                    return java.util.Optional.ofNullable(rs.getString("owner_hash"));
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to read owner hash for character " + characterId, e);
            }
        }
    }

    private static final List<String> CURRENT_STATE_TABLES = List.of(
            "tokens", "asset_current", "market_order_current", "character_contract", "industry_job",
            "character_loyalty_points");

    public void remove(long characterId) {
        synchronized (database) {
            Connection connection = database.connection();
            try {
                connection.setAutoCommit(false);
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE characters SET removed_at = ? WHERE character_id = ?")) {
                    ps.setString(1, Instant.now().toString());
                    ps.setLong(2, characterId);
                    ps.executeUpdate();
                }
                for (String table : CURRENT_STATE_TABLES) {
                    try (PreparedStatement ps = connection.prepareStatement(
                            "DELETE FROM " + table + " WHERE character_id = ?")) {
                        ps.setLong(1, characterId);
                        ps.executeUpdate();
                    }
                }
                connection.commit();
            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException ignored) {
                }
                throw new IllegalStateException("Failed to remove character " + characterId, e);
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
            }
        }
    }

    public List<EveCharacter> listAll() {
        String sql = "SELECT * FROM characters WHERE removed_at IS NULL ORDER BY character_name";
        List<EveCharacter> result = new ArrayList<>();
        synchronized (database) {
            Connection connection = database.connection();
            try (PreparedStatement ps = connection.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to list characters", e);
            }
        }
        return result;
    }

    private EveCharacter map(ResultSet rs) throws SQLException {
        String scopesRaw = rs.getString("scopes");
        List<String> scopes = scopesRaw == null || scopesRaw.isBlank()
                ? List.of()
                : List.of(scopesRaw.split(" "));
        long corpIdRaw = rs.getLong("corporation_id");
        Long corporationId = rs.wasNull() ? null : corpIdRaw;
        return new EveCharacter(
                rs.getLong("character_id"),
                rs.getString("character_name"),
                corporationId,
                scopes,
                Instant.parse(rs.getString("added_at")),
                rs.getInt("enabled") == 1
        );
    }
}
