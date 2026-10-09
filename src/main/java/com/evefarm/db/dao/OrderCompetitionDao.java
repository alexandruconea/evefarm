package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.db.JdbcUtil;
import com.evefarm.model.OrderCompetition;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class OrderCompetitionDao {

    private final Database database;

    public OrderCompetitionDao(Database database) {
        this.database = database;
    }

    public void replaceForCharacter(long characterId, List<OrderCompetition> competition) {
        database.transaction("Failed to save the competing market orders", connection -> {
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM order_competition WHERE character_id = ?")) {
                delete.setLong(1, characterId);
                delete.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT OR REPLACE INTO order_competition(order_id, character_id, best_price, outbid, checked_at)
                    VALUES (?, ?, ?, ?, ?)
                    """)) {
                for (OrderCompetition row : competition) {
                    insert.setLong(1, row.orderId());
                    insert.setLong(2, characterId);
                    JdbcUtil.setNullable(insert, 3, row.bestPrice());
                    insert.setInt(4, row.outbid() ? 1 : 0);
                    insert.setString(5, row.checkedAt().toString());
                    insert.addBatch();
                }
                insert.executeBatch();
            }
        });
    }

    public Map<Long, OrderCompetition> findCheckedSince(Instant since) {
        synchronized (database) {
            try (PreparedStatement ps = database.connection().prepareStatement(
                    "SELECT order_id, best_price, outbid, checked_at FROM order_competition");
                 ResultSet rs = ps.executeQuery()) {
                Map<Long, OrderCompetition> result = new HashMap<>();
                while (rs.next()) {
                    Instant checkedAt = Instant.parse(rs.getString("checked_at"));
                    if (!checkedAt.isBefore(since)) {
                        result.put(rs.getLong("order_id"), new OrderCompetition(rs.getLong("order_id"),
                                JdbcUtil.getNullableDouble(rs, "best_price"), rs.getInt("outbid") != 0, checkedAt));
                    }
                }
                return result;
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to read the competing market orders", e);
            }
        }
    }
}
