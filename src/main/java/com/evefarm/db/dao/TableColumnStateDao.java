package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class TableColumnStateDao {

    private final Database database;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TableColumnStateDao(Database database) {
        this.database = database;
    }

    public Optional<List<String>> find(String panelKey) {
        String sql = "SELECT columns_json FROM table_column_state WHERE panel_key = ?";
        synchronized (database) {
            Connection connection = database.connection();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, panelKey);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    String[] keys = objectMapper.readValue(rs.getString("columns_json"), String[].class);
                    return Optional.of(List.of(keys));
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to read column state for " + panelKey, e);
            } catch (Exception e) {
                return Optional.empty();
            }
        }
    }

    public void save(String panelKey, List<String> columnKeysInOrder) {
        String sql = """
                INSERT INTO table_column_state(panel_key, columns_json)
                VALUES (?, ?)
                ON CONFLICT(panel_key) DO UPDATE SET columns_json = excluded.columns_json
                """;
        synchronized (database) {
            Connection connection = database.connection();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, panelKey);
                ps.setString(2, objectMapper.writeValueAsString(columnKeysInOrder));
                ps.executeUpdate();
            } catch (Exception e) {
                throw new IllegalStateException("Failed to save column state for " + panelKey, e);
            }
        }
    }

    public Map<String, Integer> findWidths(String tableKey) {
        String sql = "SELECT widths_json FROM table_column_width WHERE table_key = ?";
        synchronized (database) {
            Connection connection = database.connection();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, tableKey);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Map.of();
                    }
                    return objectMapper.readValue(rs.getString("widths_json"),
                            new TypeReference<LinkedHashMap<String, Integer>>() {
                            });
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to read column widths for " + tableKey, e);
            } catch (Exception e) {
                return Map.of();
            }
        }
    }

    public void saveWidths(String tableKey, Map<String, Integer> widthsByColumnKey) {
        String sql = """
                INSERT INTO table_column_width(table_key, widths_json)
                VALUES (?, ?)
                ON CONFLICT(table_key) DO UPDATE SET widths_json = excluded.widths_json
                """;
        synchronized (database) {
            Connection connection = database.connection();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, tableKey);
                ps.setString(2, objectMapper.writeValueAsString(widthsByColumnKey));
                ps.executeUpdate();
            } catch (Exception e) {
                throw new IllegalStateException("Failed to save column widths for " + tableKey, e);
            }
        }
    }

    public void clearWidths(String tableKey) {
        synchronized (database) {
            Connection connection = database.connection();
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM table_column_width WHERE table_key = ?")) {
                ps.setString(1, tableKey);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to clear column widths for " + tableKey, e);
            }
        }
    }
}
