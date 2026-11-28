package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.db.JdbcUtil;
import com.evefarm.model.ContractEntry;
import com.evefarm.model.ContractItemEntry;
import com.evefarm.model.ContractRow;
import com.evefarm.model.MarketOrderEntry;
import com.evefarm.model.MarketOrderRow;

import java.sql.PreparedStatement;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

public final class CorporationMarketDao {

    private static final String ITEMS_SAVED = "saved";

    public record Order(MarketOrderEntry entry, Integer walletDivision) {
    }

    private final Database database;
    private final Clock clock;

    public CorporationMarketDao(Database database, Clock clock) {
        this.database = database;
        this.clock = clock;
    }

    public void saveOrders(long corporationId, List<Order> orders) {
        String closeSql = "UPDATE corporation_market_order SET state = ? WHERE corporation_id = ? AND state = ?";
        String insertSql = """
                INSERT INTO corporation_market_order(
                  corporation_id, order_id, wallet_division, type_id, is_buy_order, price, volume_remain,
                  volume_total, escrow, location_id, issued, duration, state, range, min_volume, fetched_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(corporation_id, order_id) DO UPDATE SET
                  wallet_division = excluded.wallet_division, type_id = excluded.type_id,
                  is_buy_order = excluded.is_buy_order, price = excluded.price,
                  volume_remain = excluded.volume_remain, volume_total = excluded.volume_total,
                  escrow = excluded.escrow, location_id = excluded.location_id, issued = excluded.issued,
                  duration = excluded.duration, state = excluded.state, range = excluded.range,
                  min_volume = excluded.min_volume, fetched_at = excluded.fetched_at
                """;
        String now = clock.instant().toString();
        database.transaction("Failed to save the market orders of corporation " + corporationId, connection -> {
            try (PreparedStatement close = connection.prepareStatement(closeSql)) {
                close.setString(1, MarketOrderRow.CLOSED);
                close.setLong(2, corporationId);
                close.setString(3, MarketOrderRow.ACTIVE);
                close.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(insertSql)) {
                for (Order order : orders) {
                    MarketOrderEntry entry = order.entry();
                    ps.setLong(1, corporationId);
                    ps.setLong(2, entry.orderId());
                    JdbcUtil.setNullable(ps, 3, order.walletDivision());
                    ps.setInt(4, entry.typeId());
                    ps.setInt(5, entry.isBuyOrder() ? 1 : 0);
                    ps.setDouble(6, entry.price());
                    ps.setLong(7, entry.volumeRemain());
                    ps.setLong(8, entry.volumeTotal());
                    JdbcUtil.setNullable(ps, 9, entry.escrow());
                    ps.setLong(10, entry.locationId());
                    ps.setString(11, entry.issued());
                    JdbcUtil.setNullable(ps, 12, entry.duration());
                    ps.setString(13, entry.state());
                    ps.setString(14, entry.range());
                    JdbcUtil.setNullable(ps, 15, entry.minVolume());
                    ps.setString(16, now);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        });
    }

    public List<MarketOrderRow> listOrderRows(boolean includeClosed) {
        String sql = """
                SELECT o.order_id, o.corporation_id, co.name AS corporation_name, co.ticker,
                       o.wallet_division, d.name AS division_name, o.type_id,
                       COALESCE(t.name, 'Type #' || o.type_id) AS type_name, t.group_name, t.category_name,
                       o.is_buy_order, o.state, o.price, o.volume_remain, o.volume_total, o.escrow, o.location_id,
                       COALESCE(l.name, 'Location #' || o.location_id) AS location_name,
                       o.issued, o.duration, o.range, o.min_volume, t.volume
                FROM corporation_market_order o
                JOIN corporation co ON co.corporation_id = o.corporation_id
                LEFT JOIN corporation_division d ON d.corporation_id = o.corporation_id AND d.kind = 'wallet'
                     AND d.division = o.wallet_division
                LEFT JOIN type_cache t ON t.type_id = o.type_id
                LEFT JOIN location_cache l ON l.location_id = o.location_id
                WHERE ? = 1 OR o.state = ?
                ORDER BY o.issued DESC
                """;
        return Queries.list(database, sql, "the corporation market orders", ps -> {
            ps.setInt(1, includeClosed ? 1 : 0);
            ps.setString(2, MarketOrderRow.ACTIVE);
        }, rs -> {
            Integer division = JdbcUtil.getNullableInt(rs, "wallet_division");
            String owner = Queries.label(rs.getString("corporation_name"), rs.getString("ticker"))
                    + (division == null ? "" : " · " + CorporationDao.walletName(division,
                    rs.getString("division_name")));
            return new MarketOrderRow(rs.getLong("order_id"), rs.getLong("corporation_id"), owner,
                    rs.getInt("type_id"), rs.getString("type_name"), rs.getString("group_name"),
                    rs.getString("category_name"), rs.getInt("is_buy_order") != 0, rs.getString("state"),
                    rs.getDouble("price"), rs.getLong("volume_remain"), rs.getLong("volume_total"),
                    JdbcUtil.getNullableDouble(rs, "escrow"), rs.getLong("location_id"), rs.getString("location_name"),
                    rs.getString("issued"), JdbcUtil.getNullableInt(rs, "duration"), rs.getString("range"),
                    JdbcUtil.getNullableLong(rs, "min_volume"), rs.getDouble("volume"), null, null, null, null, null,
                    null, null, null, null, true);
        });
    }

    public double sellOrdersValue(long corporationId) {
        return Queries.sum(database, """
                SELECT COALESCE(SUM(price * volume_remain), 0) FROM corporation_market_order
                WHERE corporation_id = ? AND is_buy_order = 0 AND state = 'active'
                """, corporationId);
    }

    public void saveContracts(long corporationId, List<ContractEntry> entries) {
        String sql = """
                INSERT INTO corporation_contract(
                  corporation_id, contract_id, type, status, title, collateral, price, reward, volume,
                  date_issued, date_expired, date_completed, issuer_id, assignee_id, acceptor_id,
                  start_location_id, end_location_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(corporation_id, contract_id) DO UPDATE SET
                  type = excluded.type, status = excluded.status, title = excluded.title,
                  collateral = excluded.collateral, price = excluded.price, reward = excluded.reward,
                  volume = excluded.volume, date_issued = excluded.date_issued,
                  date_expired = excluded.date_expired, date_completed = excluded.date_completed,
                  issuer_id = excluded.issuer_id, assignee_id = excluded.assignee_id,
                  acceptor_id = excluded.acceptor_id, start_location_id = excluded.start_location_id,
                  end_location_id = excluded.end_location_id
                """;
        database.transaction("Failed to save the contracts of corporation " + corporationId, connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                for (ContractEntry entry : entries) {
                    ps.setLong(1, corporationId);
                    ps.setLong(2, entry.contractId());
                    ps.setString(3, entry.type());
                    ps.setString(4, entry.status());
                    ps.setString(5, entry.title());
                    JdbcUtil.setNullable(ps, 6, entry.collateral());
                    JdbcUtil.setNullable(ps, 7, entry.price());
                    JdbcUtil.setNullable(ps, 8, entry.reward());
                    JdbcUtil.setNullable(ps, 9, entry.volume());
                    ps.setString(10, entry.dateIssued());
                    ps.setString(11, entry.dateExpired());
                    ps.setString(12, entry.dateCompleted());
                    JdbcUtil.setNullable(ps, 13, entry.issuerId());
                    JdbcUtil.setNullable(ps, 14, entry.assigneeId());
                    JdbcUtil.setNullable(ps, 15, entry.acceptorId());
                    JdbcUtil.setNullable(ps, 16, entry.startLocationId());
                    JdbcUtil.setNullable(ps, 17, entry.endLocationId());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        });
    }

    public List<ContractRow> listContractRows() {
        String sql = """
                SELECT ct.corporation_id, co.name AS corporation_name, co.ticker, ct.contract_id, ct.type,
                       ct.status, ct.title, ct.collateral, ct.price, ct.reward, ct.volume,
                       ct.date_issued, ct.date_expired, ct.date_completed,
                       CASE WHEN ct.issuer_id IS NULL THEN NULL
                            ELSE COALESCE(iss.name, '#' || ct.issuer_id) END AS issuer_name,
                       CASE WHEN ct.assignee_id IS NULL OR ct.assignee_id = 0 THEN NULL
                            ELSE COALESCE(asg.name, '#' || ct.assignee_id) END AS assignee_name,
                       CASE WHEN ct.acceptor_id IS NULL OR ct.acceptor_id = 0 THEN NULL
                            ELSE COALESCE(acc.name, '#' || ct.acceptor_id) END AS acceptor_name,
                       CASE WHEN ct.start_location_id IS NULL THEN NULL
                            ELSE COALESCE(sl.name, 'Location #' || ct.start_location_id) END AS start_location_name,
                       CASE WHEN ct.end_location_id IS NULL THEN NULL
                            ELSE COALESCE(el.name, 'Location #' || ct.end_location_id) END AS end_location_name
                FROM corporation_contract ct
                JOIN corporation co ON co.corporation_id = ct.corporation_id
                LEFT JOIN entity_name_cache iss ON iss.entity_id = ct.issuer_id
                LEFT JOIN entity_name_cache asg ON asg.entity_id = ct.assignee_id
                LEFT JOIN entity_name_cache acc ON acc.entity_id = ct.acceptor_id
                LEFT JOIN location_cache sl ON sl.location_id = ct.start_location_id
                LEFT JOIN location_cache el ON el.location_id = ct.end_location_id
                ORDER BY ct.date_issued DESC
                """;
        return Queries.list(database, sql, "the corporation contracts", ps -> {
        }, rs -> new ContractRow(rs.getLong("corporation_id"),
                Queries.label(rs.getString("corporation_name"), rs.getString("ticker")), rs.getLong("contract_id"),
                rs.getString("type"), rs.getString("status"), rs.getString("title"),
                JdbcUtil.getNullableDouble(rs, "collateral"), JdbcUtil.getNullableDouble(rs, "price"),
                JdbcUtil.getNullableDouble(rs, "reward"), JdbcUtil.getNullableDouble(rs, "volume"),
                rs.getString("date_issued"), rs.getString("date_expired"), rs.getString("date_completed"),
                rs.getString("issuer_name"), rs.getString("assignee_name"), rs.getString("acceptor_name"),
                rs.getString("start_location_name"), rs.getString("end_location_name"), true));
    }

    public Optional<List<ContractItemEntry>> findContractItems(long corporationId, long contractId) {
        List<String> status = Queries.list(database,
                "SELECT items_status FROM corporation_contract WHERE corporation_id = ? AND contract_id = ?",
                "the items of corporation contract " + contractId, ps -> {
                    ps.setLong(1, corporationId);
                    ps.setLong(2, contractId);
                }, rs -> String.valueOf(rs.getString(1)));
        if (status.isEmpty() || !ITEMS_SAVED.equals(status.getFirst())) {
            return Optional.empty();
        }
        return Optional.of(Queries.list(database, """
                SELECT record_id, type_id, quantity, raw_quantity, is_included FROM corporation_contract_item
                WHERE corporation_id = ? AND contract_id = ?
                ORDER BY record_id
                """, "the items of corporation contract " + contractId, ps -> {
            ps.setLong(1, corporationId);
            ps.setLong(2, contractId);
        }, rs -> new ContractItemEntry(rs.getLong("record_id"), rs.getInt("type_id"), rs.getLong("quantity"),
                JdbcUtil.getNullableInt(rs, "raw_quantity"), rs.getInt("is_included") != 0)));
    }

    public void saveContractItems(long corporationId, long contractId, List<ContractItemEntry> items) {
        database.transaction("Failed to save the items of corporation contract " + contractId, connection -> {
            try (PreparedStatement del = connection.prepareStatement(
                    "DELETE FROM corporation_contract_item WHERE corporation_id = ? AND contract_id = ?")) {
                del.setLong(1, corporationId);
                del.setLong(2, contractId);
                del.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO corporation_contract_item(
                      corporation_id, contract_id, record_id, type_id, quantity, raw_quantity, is_included)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """)) {
                for (ContractItemEntry item : items) {
                    ps.setLong(1, corporationId);
                    ps.setLong(2, contractId);
                    ps.setLong(3, item.recordId());
                    ps.setInt(4, item.typeId());
                    ps.setLong(5, item.quantity());
                    JdbcUtil.setNullable(ps, 6, item.rawQuantity());
                    ps.setInt(7, item.included() ? 1 : 0);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE corporation_contract SET items_status = ? WHERE corporation_id = ? AND contract_id = ?")) {
                ps.setString(1, ITEMS_SAVED);
                ps.setLong(2, corporationId);
                ps.setLong(3, contractId);
                ps.executeUpdate();
            }
        });
    }
}
