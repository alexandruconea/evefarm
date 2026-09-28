package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.db.JdbcUtil;
import com.evefarm.model.AbyssFleet;
import com.evefarm.model.AbyssTier;
import com.evefarm.model.AbyssWeather;
import com.evefarm.model.AbyssalCargo;
import com.evefarm.model.AbyssalLoot;
import com.evefarm.model.AbyssalRun;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class AbyssalRunDao {

    private final Database database;

    public AbyssalRunDao(Database database) {
        this.database = database;
    }

    public long save(AbyssalRun run, List<AbyssalLoot> loot) {
        return save(run, loot, null);
    }

    public long save(AbyssalRun run, List<AbyssalLoot> loot, AbyssalCargo cargo) {
        synchronized (database) {
            Connection connection = database.connection();
            try {
                connection.setAutoCommit(false);
                long id = run.id() > 0 ? update(connection, run) : insert(connection, run);
                try (PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM abyssal_run_loot WHERE run_id = ?")) {
                    delete.setLong(1, id);
                    delete.executeUpdate();
                }
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO abyssal_run_loot(run_id, type_id, type_name, quantity, unit_price) "
                                + "VALUES (?, ?, ?, ?, ?)")) {
                    for (AbyssalLoot item : loot) {
                        insert.setLong(1, id);
                        insert.setInt(2, item.typeId());
                        insert.setString(3, item.typeName());
                        insert.setLong(4, item.quantity());
                        JdbcUtil.setNullable(insert, 5, item.unitPrice());
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
                if (cargo != null) {
                    try (PreparedStatement upsert = connection.prepareStatement(
                            "INSERT OR REPLACE INTO abyssal_run_cargo(run_id, cargo_before, cargo_after) "
                                    + "VALUES (?, ?, ?)")) {
                        upsert.setLong(1, id);
                        upsert.setString(2, cargo.before());
                        upsert.setString(3, cargo.after());
                        upsert.executeUpdate();
                    }
                }
                connection.commit();
                return id;
            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException ignored) {
                }
                throw new IllegalStateException("Failed to save the Abyssal run", e);
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
            }
        }
    }

    private static long insert(Connection connection, AbyssalRun run) throws SQLException {
        String sql = """
                INSERT INTO abyssal_run(character_id, started_at, duration_seconds, tier, weather, fleet,
                  ship_type_id, ship_name, survived, loot_value, filament_cost, notes)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, run);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private static long update(Connection connection, AbyssalRun run) throws SQLException {
        String sql = """
                UPDATE abyssal_run SET character_id = ?, started_at = ?, duration_seconds = ?, tier = ?, weather = ?,
                  fleet = ?, ship_type_id = ?, ship_name = ?, survived = ?, loot_value = ?, filament_cost = ?,
                  notes = ?
                WHERE id = ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, run);
            ps.setLong(13, run.id());
            ps.executeUpdate();
            return run.id();
        }
    }

    private static void bind(PreparedStatement ps, AbyssalRun run) throws SQLException {
        ps.setLong(1, run.characterId());
        ps.setString(2, run.startedAt().toString());
        JdbcUtil.setNullable(ps, 3, run.durationSeconds());
        JdbcUtil.setNullable(ps, 4, run.tier() == null ? null : run.tier().level());
        ps.setString(5, run.weather() == null ? null : run.weather().name());
        ps.setString(6, run.fleet() == null ? null : run.fleet().name());
        JdbcUtil.setNullable(ps, 7, run.shipTypeId());
        ps.setString(8, run.shipName());
        ps.setInt(9, run.survived() ? 1 : 0);
        ps.setDouble(10, run.lootValue());
        JdbcUtil.setNullable(ps, 11, run.filamentCost());
        ps.setString(12, run.notes());
    }

    public void delete(long runId) {
        synchronized (database) {
            try (PreparedStatement ps = database.connection().prepareStatement("DELETE FROM abyssal_run WHERE id = ?")) {
                ps.setLong(1, runId);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to delete Abyssal run " + runId, e);
            }
        }
    }

    public List<AbyssalRun> listRuns() {
        String sql = """
                SELECT r.*, c.character_name
                FROM abyssal_run r
                JOIN characters c ON c.character_id = r.character_id
                WHERE c.removed_at IS NULL
                ORDER BY r.started_at DESC, r.id DESC
                """;
        synchronized (database) {
            List<AbyssalRun> runs = new ArrayList<>();
            try (Statement statement = database.connection().createStatement();
                 ResultSet rs = statement.executeQuery(sql)) {
                while (rs.next()) {
                    Long tier = JdbcUtil.getNullableLong(rs, "tier");
                    String weather = rs.getString("weather");
                    String fleet = rs.getString("fleet");
                    Long shipTypeId = JdbcUtil.getNullableLong(rs, "ship_type_id");
                    Long duration = JdbcUtil.getNullableLong(rs, "duration_seconds");
                    runs.add(new AbyssalRun(
                            rs.getLong("id"),
                            rs.getLong("character_id"),
                            rs.getString("character_name"),
                            Instant.parse(rs.getString("started_at")),
                            duration == null ? null : duration.intValue(),
                            tier == null ? null : tierOf(tier.intValue()),
                            weather == null ? null : AbyssWeather.valueOf(weather),
                            fleet == null ? null : AbyssFleet.valueOf(fleet),
                            shipTypeId == null ? null : shipTypeId.intValue(),
                            rs.getString("ship_name"),
                            rs.getInt("survived") != 0,
                            rs.getDouble("loot_value"),
                            JdbcUtil.getNullableDouble(rs, "filament_cost"),
                            rs.getString("notes")));
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to list Abyssal runs", e);
            }
            return runs;
        }
    }

    public List<AbyssalLoot> listLoot(long runId) {
        String sql = "SELECT type_id, type_name, quantity, unit_price FROM abyssal_run_loot WHERE run_id = ? "
                + "ORDER BY quantity * COALESCE(unit_price, 0) DESC, type_name";
        synchronized (database) {
            List<AbyssalLoot> loot = new ArrayList<>();
            try (PreparedStatement ps = database.connection().prepareStatement(sql)) {
                ps.setLong(1, runId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        loot.add(new AbyssalLoot(rs.getInt("type_id"), rs.getString("type_name"),
                                rs.getLong("quantity"), JdbcUtil.getNullableDouble(rs, "unit_price")));
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to list the loot of Abyssal run " + runId, e);
            }
            return loot;
        }
    }

    public Optional<AbyssalCargo> findCargo(long runId) {
        String sql = "SELECT cargo_before, cargo_after FROM abyssal_run_cargo WHERE run_id = ?";
        synchronized (database) {
            try (PreparedStatement ps = database.connection().prepareStatement(sql)) {
                ps.setLong(1, runId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next()
                            ? Optional.of(new AbyssalCargo(rs.getString("cargo_before"), rs.getString("cargo_after")))
                            : Optional.empty();
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to read the cargo of Abyssal run " + runId, e);
            }
        }
    }

    public Map<Integer, String> listIgnoredItems() {
        String sql = "SELECT type_id, type_name FROM abyssal_ignored_item ORDER BY type_name COLLATE NOCASE";
        synchronized (database) {
            Map<Integer, String> items = new LinkedHashMap<>();
            try (Statement statement = database.connection().createStatement();
                 ResultSet rs = statement.executeQuery(sql)) {
                while (rs.next()) {
                    items.put(rs.getInt("type_id"), rs.getString("type_name"));
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to list the items ignored as Abyssal loot", e);
            }
            return items;
        }
    }

    public void ignoreItem(int typeId, String typeName) {
        synchronized (database) {
            try (PreparedStatement ps = database.connection().prepareStatement(
                    "INSERT OR REPLACE INTO abyssal_ignored_item(type_id, type_name) VALUES (?, ?)")) {
                ps.setInt(1, typeId);
                ps.setString(2, typeName);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to ignore item " + typeId + " as Abyssal loot", e);
            }
        }
    }

    public void unignoreItem(int typeId) {
        synchronized (database) {
            try (PreparedStatement ps = database.connection().prepareStatement(
                    "DELETE FROM abyssal_ignored_item WHERE type_id = ?")) {
                ps.setInt(1, typeId);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to stop ignoring item " + typeId, e);
            }
        }
    }

    private static AbyssTier tierOf(int level) {
        for (AbyssTier tier : AbyssTier.values()) {
            if (tier.level() == level) {
                return tier;
            }
        }
        return null;
    }
}
