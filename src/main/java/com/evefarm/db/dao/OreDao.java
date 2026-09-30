package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.db.JdbcUtil;
import com.evefarm.model.Ore;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class OreDao {

    private final Database database;

    public OreDao(Database database) {
        this.database = database;
    }

    public void replaceAll(Collection<Ore> ores) {
        synchronized (database) {
            Connection connection = database.connection();
            try {
                connection.setAutoCommit(false);
                try (Statement statement = connection.createStatement()) {
                    statement.execute("DELETE FROM sde_ore_material");
                    statement.execute("DELETE FROM sde_ore");
                }
                try (PreparedStatement ore = connection.prepareStatement(
                        "INSERT INTO sde_ore(type_id, portion_size, compressed_type_id) VALUES (?, ?, ?)");
                     PreparedStatement material = connection.prepareStatement(
                             "INSERT INTO sde_ore_material(type_id, material_type_id, quantity) VALUES (?, ?, ?)")) {
                    for (Ore entry : ores) {
                        ore.setInt(1, entry.typeId());
                        ore.setInt(2, entry.portionSize());
                        JdbcUtil.setNullable(ore, 3, entry.compressedTypeId());
                        ore.addBatch();
                        for (Map.Entry<Integer, Long> part : entry.materials().entrySet()) {
                            material.setInt(1, entry.typeId());
                            material.setInt(2, part.getKey());
                            material.setLong(3, part.getValue());
                            material.addBatch();
                        }
                    }
                    ore.executeBatch();
                    material.executeBatch();
                }
                connection.commit();
            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException ignored) {
                }
                throw new IllegalStateException("Failed to save the ore catalog", e);
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
            try (Statement statement = database.connection().createStatement();
                 ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM sde_ore")) {
                rs.next();
                return rs.getInt(1);
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to count the ore catalog", e);
            }
        }
    }

    public Map<Integer, Ore> loadAll() {
        synchronized (database) {
            Connection connection = database.connection();
            Map<Integer, Map<Integer, Long>> materials = new HashMap<>();
            Map<Integer, Ore> result = new LinkedHashMap<>();
            try (Statement statement = connection.createStatement()) {
                try (ResultSet rs = statement.executeQuery(
                        "SELECT type_id, material_type_id, quantity FROM sde_ore_material")) {
                    while (rs.next()) {
                        materials.computeIfAbsent(rs.getInt(1), ignored -> new LinkedHashMap<>())
                                .put(rs.getInt(2), rs.getLong(3));
                    }
                }
                try (ResultSet rs = statement.executeQuery(
                        "SELECT type_id, portion_size, compressed_type_id FROM sde_ore")) {
                    while (rs.next()) {
                        int typeId = rs.getInt("type_id");
                        result.put(typeId, new Ore(typeId, rs.getInt("portion_size"),
                                JdbcUtil.getNullableInt(rs, "compressed_type_id"),
                                Map.copyOf(materials.getOrDefault(typeId, Map.of()))));
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to load the ore catalog", e);
            }
            return result;
        }
    }
}
