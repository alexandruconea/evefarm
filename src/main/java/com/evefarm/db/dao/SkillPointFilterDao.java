package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.model.SkillPointFilter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class SkillPointFilterDao {

    private final Database database;

    public SkillPointFilterDao(Database database) {
        this.database = database;
    }

    public SkillPointFilter find(long characterId) {
        String sql = "SELECT enabled, minimum_sp FROM tracker_skill_point_filter WHERE character_id = ?";
        Connection connection = database.connection();
        synchronized (database) {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, characterId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return new SkillPointFilter(characterId, true, 0);
                    }
                    return new SkillPointFilter(characterId, rs.getInt("enabled") != 0, rs.getLong("minimum_sp"));
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to read skill point filter for character " + characterId, e);
            }
        }
    }

    public void upsert(long characterId, boolean enabled, long minimumSp) {
        String sql = """
                INSERT INTO tracker_skill_point_filter(character_id, enabled, minimum_sp)
                VALUES (?, ?, ?)
                ON CONFLICT(character_id) DO UPDATE SET
                  enabled = excluded.enabled,
                  minimum_sp = excluded.minimum_sp
                """;
        Connection connection = database.connection();
        synchronized (database) {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, characterId);
                ps.setInt(2, enabled ? 1 : 0);
                ps.setLong(3, minimumSp);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to save skill point filter for character " + characterId, e);
            }
        }
    }
}
