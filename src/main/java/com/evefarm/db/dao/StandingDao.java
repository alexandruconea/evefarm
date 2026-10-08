package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.model.StandingEntry;
import com.evefarm.model.StandingRow;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class StandingDao {

    private final Database database;

    public StandingDao(Database database) {
        this.database = database;
    }

    public void replaceForCharacter(long characterId, List<StandingEntry> entries) {
        String insertSql = """
                INSERT INTO character_standings(character_id, from_id, from_type, standing, fetched_at)
                VALUES (?, ?, ?, ?, ?)
                """;
        String now = Instant.now().toString();
        database.transaction("Failed to save the standings of character " + characterId, connection -> {
            try (PreparedStatement del = connection.prepareStatement(
                    "DELETE FROM character_standings WHERE character_id = ?")) {
                del.setLong(1, characterId);
                del.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(insertSql)) {
                for (StandingEntry entry : entries) {
                    ps.setLong(1, characterId);
                    ps.setLong(2, entry.fromId());
                    ps.setString(3, entry.fromType());
                    ps.setDouble(4, entry.standing());
                    ps.setString(5, now);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        });
    }

    public List<StandingRow> listRows() {
        String sql = """
                SELECT s.character_id, c.character_name, s.from_id, s.from_type, s.standing,
                       COALESCE(a.agent_name, n.name, '#' || s.from_id) AS name,
                       a.corporation_name,
                       CASE WHEN s.from_type = 'faction' THEN NULL
                            ELSE COALESCE(a.faction_name, (SELECT x.faction_name FROM sde_agent x
                                WHERE x.corporation_id = s.from_id AND x.faction_name IS NOT NULL LIMIT 1))
                       END AS faction_name,
                       a.level, a.division_name, a.solar_system_name
                FROM character_standings s
                JOIN characters c ON c.character_id = s.character_id
                LEFT JOIN entity_name_cache n ON n.entity_id = s.from_id
                LEFT JOIN sde_agent a ON s.from_type = 'agent' AND a.agent_id = s.from_id
                WHERE c.removed_at IS NULL
                ORDER BY c.character_name,
                         CASE s.from_type WHEN 'faction' THEN 0 WHEN 'npc_corp' THEN 1 ELSE 2 END,
                         s.standing DESC, name
                """;
        synchronized (database) {
            List<StandingRow> result = new ArrayList<>();
            try (PreparedStatement ps = database.connection().prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int levelValue = rs.getInt("level");
                    Integer level = rs.wasNull() ? null : levelValue;
                    result.add(new StandingRow(
                            rs.getLong("character_id"),
                            rs.getString("character_name"),
                            rs.getLong("from_id"),
                            rs.getString("from_type"),
                            rs.getString("name"),
                            rs.getDouble("standing"),
                            rs.getString("corporation_name"),
                            rs.getString("faction_name"),
                            level,
                            rs.getString("division_name"),
                            rs.getString("solar_system_name")));
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to list the standings", e);
            }
            return result;
        }
    }
}
