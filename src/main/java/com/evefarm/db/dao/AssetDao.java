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
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class AssetDao {

    private final Database database;

    public AssetDao(Database database) {
        this.database = database;
    }

    public void replaceForCharacter(long characterId, List<AssetEntry> entries) {
        synchronized (database) {
            Connection connection = database.connection();
            String deleteSql = "DELETE FROM asset_current WHERE character_id = ?";
            String insertSql = """
                    INSERT INTO asset_current(
                      character_id, item_id, type_id, quantity, location_id, location_flag,
                      is_singleton, name, container_name, unit_price, total_value, fetched_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
            String now = Instant.now().toString();
            try {
                connection.setAutoCommit(false);
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
                connection.commit();
            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException ignored) {
                }
                throw new IllegalStateException("Failed to replace assets for character " + characterId, e);
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
            }
        }
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

    public List<AssetRow> listRows(Set<Long> characterIdFilter) {
        StringBuilder sql = new StringBuilder("""
                SELECT a.item_id, a.character_id, c.character_name, a.type_id,
                       COALESCE(t.name, 'Type #' || a.type_id) AS type_name,
                       t.group_name, t.category_name, a.quantity,
                       COALESCE(l.name, CASE WHEN a.location_id IS NULL THEN 'Unknown'
                                             ELSE 'Location #' || a.location_id END) AS location_name,
                       a.container_name,
                       a.location_flag, a.is_singleton, t.volume,
                       a.unit_price, a.total_value
                FROM asset_current a
                JOIN characters c ON c.character_id = a.character_id
                LEFT JOIN type_cache t ON t.type_id = a.type_id
                LEFT JOIN location_cache l ON l.location_id = a.location_id
                """);
        if (characterIdFilter != null && !characterIdFilter.isEmpty()) {
            String placeholders = characterIdFilter.stream().map(id -> "?").collect(Collectors.joining(","));
            sql.append(" WHERE a.character_id IN (").append(placeholders).append(")");
        }
        sql.append(" ORDER BY a.total_value DESC");

        synchronized (database) {
            Connection connection = database.connection();
            List<AssetRow> result = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
                if (characterIdFilter != null && !characterIdFilter.isEmpty()) {
                    int index = 1;
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
