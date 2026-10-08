package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.db.JdbcUtil;
import com.evefarm.model.LoyaltyPointEntry;
import com.evefarm.model.LoyaltyPointHistoryRow;
import com.evefarm.model.LoyaltyPointRow;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class LoyaltyPointDao {

    private final Database database;

    public LoyaltyPointDao(Database database) {
        this.database = database;
    }

    public void replaceForCharacter(long characterId, List<LoyaltyPointEntry> entries) {
        String deleteSql = "DELETE FROM character_loyalty_points WHERE character_id = ?";
        String insertSql = """
                INSERT INTO character_loyalty_points(character_id, corporation_id, loyalty_points, fetched_at)
                VALUES (?, ?, ?, ?)
                """;
        String now = Instant.now().toString();
        database.transaction("Failed to replace loyalty points for character " + characterId, connection -> {
            try (PreparedStatement del = connection.prepareStatement(deleteSql)) {
                del.setLong(1, characterId);
                del.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(insertSql)) {
                for (LoyaltyPointEntry entry : entries) {
                    ps.setLong(1, characterId);
                    ps.setLong(2, entry.corporationId());
                    ps.setLong(3, entry.loyaltyPoints());
                    ps.setString(4, now);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            recordChanges(connection, characterId, entries, now);
        });
    }

    private static void recordChanges(Connection connection, long characterId, List<LoyaltyPointEntry> entries,
                                      String now) throws SQLException {
        Map<Long, Long> last = new HashMap<>();
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT h.corporation_id, h.loyalty_points FROM loyalty_point_history h
                WHERE h.character_id = ? AND h.recorded_at = (
                  SELECT MAX(x.recorded_at) FROM loyalty_point_history x
                  WHERE x.character_id = h.character_id AND x.corporation_id = h.corporation_id)
                """)) {
            ps.setLong(1, characterId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    last.put(rs.getLong(1), rs.getLong(2));
                }
            }
        }
        Map<Long, Long> current = new HashMap<>();
        for (LoyaltyPointEntry entry : entries) {
            current.put(entry.corporationId(), entry.loyaltyPoints());
        }
        Set<Long> corporations = new TreeSet<>(last.keySet());
        corporations.addAll(current.keySet());
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT OR REPLACE INTO loyalty_point_history(character_id, corporation_id, loyalty_points, recorded_at)
                VALUES (?, ?, ?, ?)
                """)) {
            for (long corporationId : corporations) {
                long points = current.getOrDefault(corporationId, 0L);
                Long previous = last.get(corporationId);
                if (previous == null ? points == 0 : previous == points) {
                    continue;
                }
                ps.setLong(1, characterId);
                ps.setLong(2, corporationId);
                ps.setLong(3, points);
                ps.setString(4, now);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public List<LoyaltyPointHistoryRow> listHistory() {
        String sql = """
                SELECT h.character_id, c.character_name, h.corporation_id,
                       COALESCE(e.name, 'Corporation #' || h.corporation_id) AS corporation_name,
                       h.loyalty_points,
                       h.loyalty_points - LAG(h.loyalty_points) OVER (
                         PARTITION BY h.character_id, h.corporation_id ORDER BY h.recorded_at) AS change,
                       h.recorded_at
                FROM loyalty_point_history h
                JOIN characters c ON c.character_id = h.character_id
                LEFT JOIN entity_name_cache e ON e.entity_id = h.corporation_id
                WHERE c.removed_at IS NULL
                ORDER BY h.recorded_at DESC, c.character_name, corporation_name
                """;
        synchronized (database) {
            List<LoyaltyPointHistoryRow> result = new ArrayList<>();
            try (PreparedStatement ps = database.connection().prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new LoyaltyPointHistoryRow(
                            rs.getString("character_name"),
                            rs.getLong("corporation_id"),
                            rs.getString("corporation_name"),
                            rs.getLong("loyalty_points"),
                            JdbcUtil.getNullableLong(rs, "change"),
                            rs.getString("recorded_at")));
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to list the loyalty point history", e);
            }
            return result;
        }
    }

    public List<LoyaltyPointRow> listForCharacter(long characterId) {
        String sql = """
                SELECT l.corporation_id, l.loyalty_points
                FROM character_loyalty_points l
                WHERE l.character_id = ?
                ORDER BY l.loyalty_points DESC
                """;
        synchronized (database) {
            Connection connection = database.connection();
            List<LoyaltyPointRow> result = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, characterId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(new LoyaltyPointRow(
                                rs.getLong("corporation_id"),
                                rs.getLong("loyalty_points")
                        ));
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to list loyalty points for character " + characterId, e);
            }
            return result;
        }
    }
}
