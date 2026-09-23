package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.model.LoyaltyPointEntry;
import com.evefarm.model.LoyaltyPointRow;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class LoyaltyPointDao {

    private final Database database;

    public LoyaltyPointDao(Database database) {
        this.database = database;
    }

    public void replaceForCharacter(long characterId, List<LoyaltyPointEntry> entries) {
        synchronized (database) {
            Connection connection = database.connection();
            String deleteSql = "DELETE FROM character_loyalty_points WHERE character_id = ?";
            String insertSql = """
                    INSERT INTO character_loyalty_points(character_id, corporation_id, loyalty_points, fetched_at)
                    VALUES (?, ?, ?, ?)
                    """;
            String now = Instant.now().toString();
            try {
                connection.setAutoCommit(false);
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
                connection.commit();
            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException ignored) {
                }
                throw new IllegalStateException("Failed to replace loyalty points for character " + characterId, e);
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
            }
        }
    }

    public List<LoyaltyPointRow> listForCharacter(long characterId) {
        String sql = """
                SELECT l.character_id, l.corporation_id, l.loyalty_points,
                       COALESCE(e.name, 'Corporation #' || l.corporation_id) AS corporation_name
                FROM character_loyalty_points l
                LEFT JOIN entity_name_cache e ON e.entity_id = l.corporation_id
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
                                rs.getLong("character_id"),
                                rs.getLong("corporation_id"),
                                rs.getString("corporation_name"),
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
