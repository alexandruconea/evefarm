package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.db.JdbcUtil;
import com.evefarm.model.LoyaltyPointCheck;
import com.evefarm.model.LoyaltyPointEntry;
import com.evefarm.model.LoyaltyPointHistoryRow;
import com.evefarm.model.LoyaltyPointRow;
import com.evefarm.model.LpGain;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class LoyaltyPointDao {

    private static final Duration SAME_DATA = Duration.ofSeconds(5);

    private final Database database;

    public LoyaltyPointDao(Database database) {
        this.database = database;
    }

    public boolean replaceForCharacter(long characterId, List<LoyaltyPointEntry> entries, Instant dataFrom,
                                       Instant nextRead) {
        String deleteSql = "DELETE FROM character_loyalty_points WHERE character_id = ?";
        String insertSql = """
                INSERT INTO character_loyalty_points(character_id, corporation_id, loyalty_points, fetched_at)
                VALUES (?, ?, ?, ?)
                """;
        String now = dataFrom.toString();
        return database.transactionResult("Failed to replace loyalty points for character " + characterId,
                connection -> {
            String previousCheck = lastCheck(connection, characterId);
            if (previousCheck != null && dataFrom.isBefore(Instant.parse(previousCheck).minus(SAME_DATA))) {
                return false;
            }
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
            recordChanges(connection, characterId, entries, now, previousCheck);
            try (PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO loyalty_point_check(character_id, checked_at, next_read_at) VALUES (?, ?, ?)
                    ON CONFLICT(character_id) DO UPDATE SET checked_at = excluded.checked_at,
                      next_read_at = excluded.next_read_at
                    """)) {
                ps.setLong(1, characterId);
                ps.setString(2, now);
                ps.setString(3, nextRead.toString());
                ps.executeUpdate();
            }
            return true;
        });
    }

    public Map<Long, LoyaltyPointCheck> checks() {
        synchronized (database) {
            Map<Long, LoyaltyPointCheck> result = new HashMap<>();
            try (PreparedStatement ps = database.connection().prepareStatement(
                    "SELECT character_id, checked_at, next_read_at FROM loyalty_point_check");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String nextRead = rs.getString("next_read_at");
                    result.put(rs.getLong("character_id"), new LoyaltyPointCheck(Instant.parse(
                            rs.getString("checked_at")), nextRead == null ? null : Instant.parse(nextRead)));
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to read when the LP was read", e);
            }
            return result;
        }
    }

    private static String lastCheck(Connection connection, long characterId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT checked_at FROM loyalty_point_check WHERE character_id = ?")) {
            ps.setLong(1, characterId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private static void recordChanges(Connection connection, long characterId, List<LoyaltyPointEntry> entries,
                                      String now, String previousCheck) throws SQLException {
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
                INSERT OR REPLACE INTO loyalty_point_history(character_id, corporation_id, loyalty_points, recorded_at,
                  checked_since)
                VALUES (?, ?, ?, ?, ?)
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
                ps.setString(5, previousCheck);
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

    public List<LpGain> listGains() {
        String sql = """
                SELECT character_id, character_name, corporation_name, gained, recorded_at, checked_since
                FROM (
                  SELECT h.character_id, c.character_name,
                         COALESCE(e.name, 'Corporation #' || h.corporation_id) AS corporation_name,
                         h.loyalty_points - COALESCE(LAG(h.loyalty_points) OVER corporation,
                           CASE WHEN h.checked_since IS NOT NULL
                                  OR h.recorded_at > MIN(h.recorded_at) OVER (PARTITION BY h.character_id)
                                THEN 0 ELSE h.loyalty_points END) AS gained,
                         h.recorded_at, h.checked_since
                  FROM loyalty_point_history h
                  JOIN characters c ON c.character_id = h.character_id
                  LEFT JOIN entity_name_cache e ON e.entity_id = h.corporation_id
                  WHERE c.removed_at IS NULL
                  WINDOW corporation AS (PARTITION BY h.character_id, h.corporation_id ORDER BY h.recorded_at)
                )
                WHERE gained > 0
                ORDER BY recorded_at, character_name, corporation_name
                """;
        synchronized (database) {
            List<LpGain> result = new ArrayList<>();
            try (PreparedStatement ps = database.connection().prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String since = rs.getString("checked_since");
                    result.add(new LpGain(rs.getLong("character_id"), rs.getString("character_name"),
                            rs.getString("corporation_name"), rs.getLong("gained"),
                            Instant.parse(rs.getString("recorded_at")), since == null ? null : Instant.parse(since)));
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to read the LP earned", e);
            }
            return result;
        }
    }

    public Map<Long, Instant> firstRecorded() {
        String sql = """
                SELECT h.character_id, MIN(h.recorded_at) AS first_recorded
                FROM loyalty_point_history h
                JOIN characters c ON c.character_id = h.character_id
                WHERE c.removed_at IS NULL
                GROUP BY h.character_id
                """;
        synchronized (database) {
            Map<Long, Instant> result = new HashMap<>();
            try (PreparedStatement ps = database.connection().prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getLong("character_id"), Instant.parse(rs.getString("first_recorded")));
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to read when the LP history starts", e);
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
