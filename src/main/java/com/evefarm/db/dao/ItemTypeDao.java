package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.model.ItemType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class ItemTypeDao {

    private final Database database;

    public ItemTypeDao(Database database) {
        this.database = database;
    }

    public void replaceAll(List<ItemType> items) {
        synchronized (database) {
            Connection connection = database.connection();
            String insertSql = "INSERT INTO sde_item_type(type_id, type_name, is_officer) VALUES (?, ?, ?)";
            try {
                connection.setAutoCommit(false);
                try (Statement statement = connection.createStatement()) {
                    statement.execute("DELETE FROM sde_item_type");
                }
                try (PreparedStatement ps = connection.prepareStatement(insertSql)) {
                    for (ItemType item : items) {
                        ps.setInt(1, item.typeId());
                        ps.setString(2, item.typeName());
                        ps.setInt(3, item.officer() ? 1 : 0);
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
                throw new IllegalStateException("Failed to replace the item catalog", e);
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
            }
        }
    }

    public int count() {
        synchronized (database) {
            Connection connection = database.connection();
            try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM sde_item_type");
                 ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to count item types", e);
            }
        }
    }

    public List<ItemType> search(String text, int limit) {
        String sql = """
                SELECT type_id, type_name, is_officer FROM sde_item_type
                WHERE type_name LIKE ? ESCAPE '\\'
                ORDER BY is_officer DESC, type_name LIMIT ?
                """;
        String pattern = "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return query(sql, pattern, limit);
    }

    public List<ItemType> listOfficerItems(String prefix) {
        String sql = """
                SELECT type_id, type_name, is_officer FROM sde_item_type
                WHERE is_officer = 1 AND type_name LIKE ? ESCAPE '\\'
                ORDER BY type_name LIMIT ?
                """;
        String pattern = prefix.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return query(sql, pattern, 500);
    }

    private List<ItemType> query(String sql, String pattern, int limit) {
        synchronized (database) {
            Connection connection = database.connection();
            List<ItemType> result = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, pattern);
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(new ItemType(rs.getInt("type_id"), rs.getString("type_name"),
                                rs.getInt("is_officer") == 1));
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to search item types", e);
            }
            return result;
        }
    }
}
