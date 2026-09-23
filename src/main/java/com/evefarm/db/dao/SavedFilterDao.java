package com.evefarm.db.dao;

import com.evefarm.db.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SavedFilterDao {

    private final Database database;

    public SavedFilterDao(Database database) {
        this.database = database;
    }

    public void save(String panelKey, String name, String conditionsJson) {
        String sql = """
                INSERT INTO saved_table_filter(panel_key, name, conditions)
                VALUES (?, ?, ?)
                ON CONFLICT(panel_key, name) DO UPDATE SET conditions = excluded.conditions
                """;
        synchronized (database) {
            Connection connection = database.connection();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, panelKey);
                ps.setString(2, name);
                ps.setString(3, conditionsJson);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to save filter '" + name + "' for " + panelKey, e);
            }
        }
    }

    public List<String> listNames(String panelKey) {
        String sql = "SELECT name FROM saved_table_filter WHERE panel_key = ? ORDER BY name";
        List<String> result = new ArrayList<>();
        synchronized (database) {
            Connection connection = database.connection();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, panelKey);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(rs.getString("name"));
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to list saved filters for " + panelKey, e);
            }
        }
        return result;
    }

    public Optional<String> find(String panelKey, String name) {
        String sql = "SELECT conditions FROM saved_table_filter WHERE panel_key = ? AND name = ?";
        synchronized (database) {
            Connection connection = database.connection();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, panelKey);
                ps.setString(2, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(rs.getString("conditions"));
                    }
                    return Optional.empty();
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to read saved filter '" + name + "' for " + panelKey, e);
            }
        }
    }

    public void delete(String panelKey, String name) {
        String sql = "DELETE FROM saved_table_filter WHERE panel_key = ? AND name = ?";
        synchronized (database) {
            Connection connection = database.connection();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, panelKey);
                ps.setString(2, name);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to delete saved filter '" + name + "' for " + panelKey, e);
            }
        }
    }
}
