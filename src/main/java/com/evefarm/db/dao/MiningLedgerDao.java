package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.model.MiningEntry;
import com.evefarm.model.MiningLedgerRow;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class MiningLedgerDao {

    private final Database database;

    public MiningLedgerDao(Database database) {
        this.database = database;
    }

    public void saveForCharacter(long characterId, List<MiningEntry> entries) {
        synchronized (database) {
            Connection connection = database.connection();
            String sql = """
                    INSERT INTO mining_ledger(character_id, date, solar_system_id, type_id, quantity, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    ON CONFLICT(character_id, date, solar_system_id, type_id) DO UPDATE SET
                      quantity = excluded.quantity, updated_at = excluded.updated_at
                    """;
            String now = Instant.now().toString();
            try {
                connection.setAutoCommit(false);
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    for (MiningEntry entry : entries) {
                        ps.setLong(1, characterId);
                        ps.setString(2, entry.date());
                        ps.setLong(3, entry.solarSystemId());
                        ps.setInt(4, entry.typeId());
                        ps.setLong(5, entry.quantity());
                        ps.setString(6, now);
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
                throw new IllegalStateException("Failed to save the mining ledger of character " + characterId, e);
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
            }
        }
    }

    public List<MiningLedgerRow> listRows(String fromDate) {
        StringBuilder sql = new StringBuilder("""
                SELECT m.character_id, c.character_name, m.date, m.type_id, m.quantity,
                       COALESCE(l.name, 'System #' || m.solar_system_id) AS system_name,
                       COALESCE(t.name, 'Type #' || m.type_id) AS ore_name,
                       t.group_name, t.volume
                FROM mining_ledger m
                JOIN characters c ON c.character_id = m.character_id
                LEFT JOIN type_cache t ON t.type_id = m.type_id
                LEFT JOIN location_cache l ON l.location_id = m.solar_system_id
                WHERE c.removed_at IS NULL
                """);
        if (fromDate != null) {
            sql.append(" AND m.date >= ?");
        }
        sql.append(" ORDER BY m.date DESC, c.character_name, ore_name");
        synchronized (database) {
            List<MiningLedgerRow> result = new ArrayList<>();
            try (PreparedStatement ps = database.connection().prepareStatement(sql.toString())) {
                if (fromDate != null) {
                    ps.setString(1, fromDate);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(new MiningLedgerRow(
                                rs.getLong("character_id"),
                                rs.getString("character_name"),
                                rs.getString("date"),
                                rs.getString("system_name"),
                                rs.getInt("type_id"),
                                rs.getString("ore_name"),
                                rs.getString("group_name"),
                                rs.getLong("quantity"),
                                rs.getDouble("volume")));
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to list the mining ledger", e);
            }
            return result;
        }
    }
}
