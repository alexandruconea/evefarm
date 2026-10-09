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
import java.util.Optional;

public final class CharacterDao {

    private final Database database;

    public CharacterDao(Database database) {
        this.database = database;
    }

    public void upsert(long characterId, String characterName, List<String> scopes, String ownerHash) {
        String sql = """
                INSERT INTO characters(character_id, character_name, scopes, added_at, enabled, owner_hash)
                VALUES (?, ?, ?, ?, 1, ?)
                ON CONFLICT(character_id) DO UPDATE SET
                  character_name = excluded.character_name,
                  scopes = excluded.scopes,
                  owner_hash = excluded.owner_hash,
                  removed_at = NULL
                """;
        synchronized (database) {
            try (PreparedStatement ps = database.connection().prepareStatement(sql)) {
                ps.setLong(1, characterId);
                ps.setString(2, characterName);
                ps.setString(3, String.join(" ", scopes));
                ps.setString(4, Instant.now().toString());
                ps.setString(5, ownerHash);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to upsert character " + characterId, e);
            }
        }
    }

    public Optional<String> findOwnerHash(long characterId) {
        String sql = "SELECT owner_hash FROM characters WHERE character_id = ? AND removed_at IS NULL";
        synchronized (database) {
            try (PreparedStatement ps = database.connection().prepareStatement(sql)) {
                ps.setLong(1, characterId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    return Optional.ofNullable(rs.getString("owner_hash"));
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to read owner hash for character " + characterId, e);
            }
        }
    }

    private static final List<String> CURRENT_STATE_TABLES = List.of(
            "tokens", "asset_current", "character_loyalty_points", "character_standings",
            "character_skill", "character_attributes", "order_competition");

    public void remove(long characterId) {
        database.transaction("Failed to remove character " + characterId, connection -> {
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
        });
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
        return new EveCharacter(
                rs.getLong("character_id"),
                rs.getString("character_name"),
                scopes,
                Instant.parse(rs.getString("added_at"))
        );
    }
}
