package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.db.JdbcUtil;
import com.evefarm.model.MarketOrderEntry;
import com.evefarm.model.MarketOrderRow;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class MarketOrderDao {

    private final Database database;

    public MarketOrderDao(Database database) {
        this.database = database;
    }

    public void saveForCharacter(long characterId, List<MarketOrderEntry> entries) {
        String closeSql = "UPDATE market_order_current SET state = ? WHERE character_id = ? AND state = ?";
        String insertSql = """
                INSERT INTO market_order_current(
                  character_id, order_id, type_id, is_buy_order, price, volume_remain, volume_total,
                  escrow, location_id, issued, duration, state, range, min_volume, fetched_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(character_id, order_id) DO UPDATE SET
                  type_id = excluded.type_id, is_buy_order = excluded.is_buy_order, price = excluded.price,
                  volume_remain = excluded.volume_remain, volume_total = excluded.volume_total,
                  escrow = excluded.escrow, location_id = excluded.location_id, issued = excluded.issued,
                  duration = excluded.duration, state = excluded.state, range = excluded.range,
                  min_volume = excluded.min_volume, fetched_at = excluded.fetched_at
                """;
        String now = Instant.now().toString();
        database.transaction("Failed to save market orders for character " + characterId, connection -> {
            try (PreparedStatement close = connection.prepareStatement(closeSql)) {
                close.setString(1, MarketOrderRow.CLOSED);
                close.setLong(2, characterId);
                close.setString(3, MarketOrderRow.ACTIVE);
                close.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(insertSql)) {
                for (MarketOrderEntry entry : entries) {
                    ps.setLong(1, characterId);
                    ps.setLong(2, entry.orderId());
                    ps.setInt(3, entry.typeId());
                    ps.setInt(4, entry.isBuyOrder() ? 1 : 0);
                    ps.setDouble(5, entry.price());
                    ps.setLong(6, entry.volumeRemain());
                    ps.setLong(7, entry.volumeTotal());
                    JdbcUtil.setNullable(ps, 8, entry.escrow());
                    ps.setLong(9, entry.locationId());
                    ps.setString(10, entry.issued());
                    JdbcUtil.setNullable(ps, 11, entry.duration());
                    ps.setString(12, entry.state());
                    ps.setString(13, entry.range());
                    JdbcUtil.setNullable(ps, 14, entry.minVolume());
                    ps.setString(15, now);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        });
    }

    public List<MarketOrderRow> listRows(Set<Long> characterIdFilter, boolean includeClosed) {
        StringBuilder sql = new StringBuilder("""
                SELECT o.order_id, o.character_id, c.character_name, o.type_id,
                       COALESCE(t.name, 'Type #' || o.type_id) AS type_name,
                       t.group_name, t.category_name, o.is_buy_order, o.state, o.price, o.volume_remain,
                       o.volume_total, o.escrow,
                       COALESCE(l.name, 'Location #' || o.location_id) AS location_name,
                       o.issued, o.duration, o.range, o.min_volume, t.volume
                FROM market_order_current o
                JOIN characters c ON c.character_id = o.character_id
                LEFT JOIN type_cache t ON t.type_id = o.type_id
                LEFT JOIN location_cache l ON l.location_id = o.location_id
                WHERE c.removed_at IS NULL
                """);
        if (!includeClosed) {
            sql.append(" AND o.state = '").append(MarketOrderRow.ACTIVE).append("'");
        }
        if (characterIdFilter != null && !characterIdFilter.isEmpty()) {
            String placeholders = characterIdFilter.stream().map(id -> "?").collect(Collectors.joining(","));
            sql.append(" AND o.character_id IN (").append(placeholders).append(")");
        }
        sql.append(" ORDER BY o.issued DESC");

        synchronized (database) {
            Connection connection = database.connection();
            List<MarketOrderRow> result = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
                if (characterIdFilter != null && !characterIdFilter.isEmpty()) {
                    int index = 1;
                    for (Long id : characterIdFilter) {
                        ps.setLong(index++, id);
                    }
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(new MarketOrderRow(
                                rs.getLong("order_id"),
                                rs.getLong("character_id"),
                                rs.getString("character_name"),
                                rs.getInt("type_id"),
                                rs.getString("type_name"),
                                rs.getString("group_name"),
                                rs.getString("category_name"),
                                rs.getInt("is_buy_order") != 0,
                                rs.getString("state"),
                                rs.getDouble("price"),
                                rs.getLong("volume_remain"),
                                rs.getLong("volume_total"),
                                JdbcUtil.getNullableDouble(rs, "escrow"),
                                rs.getString("location_name"),
                                rs.getString("issued"),
                                JdbcUtil.getNullableInt(rs, "duration"),
                                rs.getString("range"),
                                JdbcUtil.getNullableLong(rs, "min_volume"),
                                rs.getDouble("volume"),
                                null, null, null, null, null, null, null, null
                        ));
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to list market order rows", e);
            }
            return result;
        }
    }
}
