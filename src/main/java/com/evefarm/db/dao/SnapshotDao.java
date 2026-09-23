package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.model.TrackerSnapshot;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class SnapshotDao {

    private final Database database;

    public SnapshotDao(Database database) {
        this.database = database;
    }

    public void insert(TrackerSnapshot snapshot) {
        String sql = """
                INSERT INTO tracker_snapshot(
                  character_id, captured_at, wallet_balance, assets_value, implants_value,
                  sell_orders_value, escrow_value, escrow_to_cover_value, manufacturing_value,
                  contract_collateral_value, contracts_value, skill_points, skill_point_value,
                  lp_value, total_value)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(character_id, captured_at) DO UPDATE SET
                  wallet_balance = excluded.wallet_balance,
                  assets_value = excluded.assets_value,
                  implants_value = excluded.implants_value,
                  sell_orders_value = excluded.sell_orders_value,
                  escrow_value = excluded.escrow_value,
                  escrow_to_cover_value = excluded.escrow_to_cover_value,
                  manufacturing_value = excluded.manufacturing_value,
                  contract_collateral_value = excluded.contract_collateral_value,
                  contracts_value = excluded.contracts_value,
                  skill_points = excluded.skill_points,
                  skill_point_value = excluded.skill_point_value,
                  lp_value = excluded.lp_value,
                  total_value = excluded.total_value
                """;
        synchronized (database) {
            Connection connection = database.connection();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, snapshot.characterId());
                ps.setString(2, snapshot.capturedAt().toString());
                ps.setDouble(3, snapshot.walletBalance());
                ps.setDouble(4, snapshot.assetsValue());
                ps.setDouble(5, snapshot.implantsValue());
                ps.setDouble(6, snapshot.sellOrdersValue());
                ps.setDouble(7, snapshot.escrowValue());
                ps.setDouble(8, snapshot.escrowToCoverValue());
                ps.setDouble(9, snapshot.manufacturingValue());
                ps.setDouble(10, snapshot.contractCollateralValue());
                ps.setDouble(11, snapshot.contractsValue());
                ps.setLong(12, snapshot.skillPoints());
                ps.setDouble(13, snapshot.skillPointValue());
                ps.setDouble(14, snapshot.lpValue());
                ps.setDouble(15, snapshot.totalValue());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to insert tracker snapshot for " + snapshot.characterId(), e);
            }
        }
    }

    public void delete(long characterId, Instant capturedAt) {
        String sql = "DELETE FROM tracker_snapshot WHERE character_id = ? AND captured_at = ?";
        synchronized (database) {
            Connection connection = database.connection();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, characterId);
                ps.setString(2, capturedAt.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to delete tracker snapshot for character " + characterId, e);
            }
        }
    }

    public Optional<TrackerSnapshot> findLatest(long characterId) {
        String sql = "SELECT * FROM tracker_snapshot WHERE character_id = ? ORDER BY captured_at DESC LIMIT 1";
        synchronized (database) {
            Connection connection = database.connection();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, characterId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(map(rs));
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to read latest tracker snapshot for " + characterId, e);
            }
        }
    }

    public List<TrackerSnapshot> listBetween(Set<Long> characterIds, Instant from, Instant to) {
        StringBuilder sql = new StringBuilder(
                "SELECT * FROM tracker_snapshot WHERE captured_at BETWEEN ? AND ? "
                        + "AND character_id IN (SELECT character_id FROM characters WHERE removed_at IS NULL)");
        if (characterIds != null && !characterIds.isEmpty()) {
            String placeholders = characterIds.stream().map(id -> "?").collect(Collectors.joining(","));
            sql.append(" AND character_id IN (").append(placeholders).append(")");
        }
        sql.append(" ORDER BY captured_at");

        List<TrackerSnapshot> result = new ArrayList<>();
        synchronized (database) {
            Connection connection = database.connection();
            try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
                ps.setString(1, from.toString());
                ps.setString(2, to.toString());
                if (characterIds != null && !characterIds.isEmpty()) {
                    int index = 3;
                    for (Long id : characterIds) {
                        ps.setLong(index++, id);
                    }
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(map(rs));
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to list tracker snapshots", e);
            }
        }
        return result;
    }

    private TrackerSnapshot map(ResultSet rs) throws SQLException {
        return new TrackerSnapshot(
                rs.getLong("character_id"),
                Instant.parse(rs.getString("captured_at")),
                rs.getDouble("wallet_balance"),
                rs.getDouble("assets_value"),
                rs.getDouble("implants_value"),
                rs.getDouble("sell_orders_value"),
                rs.getDouble("escrow_value"),
                rs.getDouble("escrow_to_cover_value"),
                rs.getDouble("manufacturing_value"),
                rs.getDouble("contract_collateral_value"),
                rs.getDouble("contracts_value"),
                rs.getLong("skill_points"),
                rs.getDouble("skill_point_value"),
                rs.getDouble("lp_value"),
                rs.getDouble("total_value")
        );
    }
}
