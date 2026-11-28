package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.db.JdbcUtil;
import com.evefarm.model.TrackerSnapshot;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** One snapshot per corporation and day: a newer capture on the same day replaces the older one. */
public final class CorporationSnapshotDao {

    private final Database database;

    public CorporationSnapshotDao(Database database) {
        this.database = database;
    }

    public void save(TrackerSnapshot snapshot, LocalDate day) {
        String sql = """
                INSERT INTO corporation_snapshot(
                  corporation_id, day, captured_at, wallet_balance, assets_value, sell_orders_value, escrow_value,
                  escrow_to_cover_value, manufacturing_value, contract_collateral_value, contracts_value, total_value)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(corporation_id, day) DO UPDATE SET
                  captured_at = excluded.captured_at, wallet_balance = excluded.wallet_balance,
                  assets_value = excluded.assets_value, sell_orders_value = excluded.sell_orders_value,
                  escrow_value = excluded.escrow_value, escrow_to_cover_value = excluded.escrow_to_cover_value,
                  manufacturing_value = excluded.manufacturing_value,
                  contract_collateral_value = excluded.contract_collateral_value,
                  contracts_value = excluded.contracts_value, total_value = excluded.total_value
                """;
        synchronized (database) {
            try (PreparedStatement ps = database.connection().prepareStatement(sql)) {
                ps.setLong(1, snapshot.characterId());
                ps.setString(2, day.toString());
                ps.setString(3, snapshot.capturedAt().toString());
                ps.setDouble(4, snapshot.walletBalance());
                ps.setDouble(5, snapshot.assetsValue());
                ps.setDouble(6, snapshot.sellOrdersValue());
                ps.setDouble(7, snapshot.escrowValue());
                ps.setDouble(8, snapshot.escrowToCoverValue());
                ps.setDouble(9, snapshot.manufacturingValue());
                ps.setDouble(10, snapshot.contractCollateralValue());
                ps.setDouble(11, snapshot.contractsValue());
                ps.setDouble(12, snapshot.totalValue());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to save the snapshot of corporation "
                        + snapshot.characterId(), e);
            }
        }
    }

    public void delete(long corporationId, Instant capturedAt) {
        synchronized (database) {
            try (PreparedStatement ps = database.connection().prepareStatement(
                    "DELETE FROM corporation_snapshot WHERE corporation_id = ? AND captured_at = ?")) {
                ps.setLong(1, corporationId);
                ps.setString(2, capturedAt.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to delete the snapshot of corporation " + corporationId, e);
            }
        }
    }

    public List<TrackerSnapshot> listBetween(Set<Long> corporationIds, Instant from, Instant to) {
        if (corporationIds.isEmpty()) {
            return List.of();
        }
        String sql = "SELECT * FROM corporation_snapshot WHERE captured_at BETWEEN ? AND ? AND corporation_id IN ("
                + JdbcUtil.placeholders(corporationIds.size()) + ") ORDER BY captured_at";
        List<Long> ids = new ArrayList<>(corporationIds);
        return Queries.list(database, sql, "the corporation snapshots", ps -> {
            ps.setString(1, from.toString());
            ps.setString(2, to.toString());
            for (int i = 0; i < ids.size(); i++) {
                ps.setLong(3 + i, ids.get(i));
            }
        }, rs -> TrackerSnapshot.ofCorporation(rs.getLong("corporation_id"),
                Instant.parse(rs.getString("captured_at")), rs.getDouble("wallet_balance"),
                rs.getDouble("assets_value"), rs.getDouble("sell_orders_value"), rs.getDouble("escrow_value"),
                rs.getDouble("escrow_to_cover_value"), rs.getDouble("manufacturing_value"),
                rs.getDouble("contract_collateral_value"), rs.getDouble("contracts_value")));
    }

    /** The corporations with at least one snapshot. */
    public List<Long> corporationIds() {
        return Queries.list(database, "SELECT DISTINCT corporation_id FROM corporation_snapshot",
                "the corporations with snapshots", ps -> {
                }, rs -> rs.getLong(1));
    }
}
