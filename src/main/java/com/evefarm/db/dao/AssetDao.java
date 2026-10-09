package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.db.JdbcUtil;
import com.evefarm.model.AssetEntry;
import com.evefarm.model.AssetRow;

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
import java.util.stream.Collectors;

public final class AssetDao {

    private final Database database;

    public AssetDao(Database database) {
        this.database = database;
    }

    public void replaceForCharacter(long characterId, List<AssetEntry> entries) {
        String deleteSql = "DELETE FROM asset_current WHERE character_id = ?";
        String insertSql = """
                INSERT INTO asset_current(
                  character_id, item_id, type_id, quantity, location_id, location_flag,
                  is_singleton, name, container_name, unit_price, total_value, fetched_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        String now = Instant.now().toString();
        database.transaction("Failed to replace assets for character " + characterId, connection -> {
            try (PreparedStatement del = connection.prepareStatement(deleteSql)) {
                del.setLong(1, characterId);
                del.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(insertSql)) {
                for (AssetEntry entry : entries) {
                    ps.setLong(1, characterId);
                    ps.setLong(2, entry.itemId());
                    ps.setInt(3, entry.typeId());
                    ps.setLong(4, entry.quantity());
                    JdbcUtil.setNullable(ps, 5, entry.locationId());
                    ps.setString(6, entry.locationFlag());
                    ps.setInt(7, entry.isSingleton() ? 1 : 0);
                    ps.setString(8, entry.name());
                    ps.setString(9, entry.containerName());
                    ps.setDouble(10, entry.unitPrice());
                    ps.setDouble(11, entry.totalValue());
                    ps.setString(12, now);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            archiveMonth(connection, characterId, now.substring(0, 7));
        });
    }

    private static void archiveMonth(Connection connection, long characterId, String month) throws SQLException {
        try (PreparedStatement del = connection.prepareStatement(
                "DELETE FROM asset_archive WHERE character_id = ? AND month = ?")) {
            del.setLong(1, characterId);
            del.setString(2, month);
            del.executeUpdate();
        }
        try (PreparedStatement copy = connection.prepareStatement("""
                INSERT INTO asset_archive(character_id, month, captured_at, item_id, type_id, quantity, location_id,
                                          location_flag, is_singleton, name, container_name, unit_price, total_value)
                SELECT character_id, ?, fetched_at, item_id, type_id, quantity, location_id, location_flag,
                       is_singleton, name, container_name, unit_price, total_value
                FROM asset_current WHERE character_id = ?
                """)) {
            copy.setString(1, month);
            copy.setLong(2, characterId);
            copy.executeUpdate();
        }
    }

    public List<ArchivedMonth> listArchivedMonths() {
        String sql = """
                SELECT a.month, MAX(a.captured_at) AS captured_at
                FROM asset_archive a
                JOIN characters c ON c.character_id = a.character_id
                WHERE c.removed_at IS NULL
                GROUP BY a.month
                ORDER BY a.month DESC
                """;
        synchronized (database) {
            List<ArchivedMonth> result = new ArrayList<>();
            try (PreparedStatement ps = database.connection().prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new ArchivedMonth(rs.getString("month"), rs.getString("captured_at")));
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to list the archived asset months", e);
            }
            return result;
        }
    }

    public record ArchivedMonth(String month, String lastSavedAt) {
    }

    public double sumTotalValue(long characterId) {
        String sql = "SELECT COALESCE(SUM(total_value), 0) AS total FROM asset_current WHERE character_id = ?";
        synchronized (database) {
            Connection connection = database.connection();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, characterId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getDouble("total");
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to sum assets for character " + characterId, e);
            }
        }
    }

    public Map<Integer, Long> usableStock(Long solarSystemId) {
        String sql = """
                SELECT a.type_id, SUM(a.quantity) AS quantity
                FROM asset_current a
                JOIN characters c ON c.character_id = a.character_id
                LEFT JOIN location_cache l ON l.location_id = a.location_id
                WHERE c.removed_at IS NULL AND a.is_singleton = 0 AND a.quantity > 0
                  AND COALESCE(a.location_flag, '') NOT LIKE '%Slot%'
                  AND COALESCE(a.location_flag, '') NOT IN ('HiddenModifiers', 'AssetSafety')
                  AND (? IS NULL OR l.system_id = ?)
                GROUP BY a.type_id
                """;
        synchronized (database) {
            try (PreparedStatement ps = database.connection().prepareStatement(sql)) {
                JdbcUtil.setNullable(ps, 1, solarSystemId);
                JdbcUtil.setNullable(ps, 2, solarSystemId);
                Map<Integer, Long> stock = new HashMap<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        stock.put(rs.getInt("type_id"), rs.getLong("quantity"));
                    }
                }
                return stock;
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to read the items in stock", e);
            }
        }
    }

    public List<AssetRow> listRows(Set<Long> characterIdFilter) {
        return queryRows("asset_current", null, characterIdFilter);
    }

    public List<AssetRow> listArchivedRows(String month, Set<Long> characterIdFilter) {
        return queryRows("asset_archive", month, characterIdFilter);
    }

    private List<AssetRow> queryRows(String table, String month, Set<Long> characterIdFilter) {
        StringBuilder sql = new StringBuilder("""
                SELECT a.item_id, a.character_id, c.character_name, a.type_id,
                       COALESCE(t.name, 'Type #' || a.type_id) AS type_name,
                       t.group_name, t.category_name, a.quantity,
                       COALESCE(l.name, CASE WHEN a.location_id IS NULL THEN 'Unknown'
                                             ELSE 'Location #' || a.location_id END) AS location_name,
                       a.container_name,
                       a.location_flag, a.is_singleton, t.volume,
                       a.unit_price, a.total_value
                FROM %s a
                JOIN characters c ON c.character_id = a.character_id
                LEFT JOIN type_cache t ON t.type_id = a.type_id
                LEFT JOIN location_cache l ON l.location_id = a.location_id
                WHERE c.removed_at IS NULL
                """.formatted(table));
        if (month != null) {
            sql.append(" AND a.month = ?");
        }
        if (characterIdFilter != null && !characterIdFilter.isEmpty()) {
            String placeholders = characterIdFilter.stream().map(id -> "?").collect(Collectors.joining(","));
            sql.append(" AND a.character_id IN (").append(placeholders).append(")");
        }
        sql.append(" ORDER BY a.total_value DESC");

        synchronized (database) {
            Connection connection = database.connection();
            List<AssetRow> result = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
                int index = 1;
                if (month != null) {
                    ps.setString(index++, month);
                }
                if (characterIdFilter != null && !characterIdFilter.isEmpty()) {
                    for (Long id : characterIdFilter) {
                        ps.setLong(index++, id);
                    }
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(new AssetRow(
                                rs.getLong("item_id"),
                                rs.getLong("character_id"),
                                rs.getString("character_name"),
                                rs.getInt("type_id"),
                                rs.getString("type_name"),
                                rs.getString("group_name"),
                                rs.getString("category_name"),
                                rs.getLong("quantity"),
                                rs.getString("location_name"),
                                rs.getString("container_name"),
                                rs.getString("location_flag"),
                                rs.getInt("is_singleton") == 1,
                                rs.getDouble("volume"),
                                rs.getDouble("unit_price"),
                                rs.getDouble("total_value")
                        ));
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to list asset rows", e);
            }
            return result;
        }
    }
}
