package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.db.JdbcUtil;
import com.evefarm.model.LpPurchase;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class LpPurchaseDao {

    private final Database database;

    public LpPurchaseDao(Database database) {
        this.database = database;
    }

    public long insert(LpPurchase purchase) {
        return database.transactionResult("Failed to save the LP Store purchase", connection -> {
            try (PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO lp_purchase(character_id, corporation_id, corporation_name, offer_id, type_id,
                      type_name, offers, units, lp_spent, isk_spent, other_cost, target_isk_per_lp, purchased_at,
                      journal_entry_id, used_units, notes)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                bind(ps, purchase);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    return keys.getLong(1);
                }
            }
        });
    }

    public void update(LpPurchase purchase) {
        database.transaction("Failed to update the LP Store purchase", connection -> {
            try (PreparedStatement ps = connection.prepareStatement("""
                    UPDATE lp_purchase SET character_id = ?, corporation_id = ?, corporation_name = ?, offer_id = ?,
                      type_id = ?, type_name = ?, offers = ?, units = ?, lp_spent = ?, isk_spent = ?, other_cost = ?,
                      target_isk_per_lp = ?, purchased_at = ?, journal_entry_id = ?, used_units = ?, notes = ?
                    WHERE purchase_id = ?
                    """)) {
                bind(ps, purchase);
                ps.setLong(17, purchase.purchaseId());
                ps.executeUpdate();
            }
        });
    }

    public void delete(long purchaseId) {
        database.transaction("Failed to delete the LP Store purchase", connection -> {
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM lp_purchase WHERE purchase_id = ?")) {
                ps.setLong(1, purchaseId);
                ps.executeUpdate();
            }
        });
    }

    public List<LpPurchase> listAll() {
        synchronized (database) {
            try (PreparedStatement ps = database.connection().prepareStatement("""
                    SELECT p.*, COALESCE(c.character_name, '#' || p.character_id) AS character_name
                    FROM lp_purchase p
                    LEFT JOIN characters c ON c.character_id = p.character_id
                    ORDER BY p.purchased_at, p.purchase_id
                    """);
                 ResultSet rs = ps.executeQuery()) {
                List<LpPurchase> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(new LpPurchase(rs.getLong("purchase_id"), rs.getLong("character_id"),
                            rs.getString("character_name"), rs.getLong("corporation_id"),
                            rs.getString("corporation_name"), rs.getLong("offer_id"), rs.getInt("type_id"),
                            rs.getString("type_name"), rs.getInt("offers"), rs.getLong("units"),
                            rs.getLong("lp_spent"), rs.getDouble("isk_spent"), rs.getDouble("other_cost"),
                            JdbcUtil.getNullableDouble(rs, "target_isk_per_lp"),
                            Instant.parse(rs.getString("purchased_at")),
                            JdbcUtil.getNullableLong(rs, "journal_entry_id"), rs.getLong("used_units"),
                            rs.getString("notes")));
                }
                return result;
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to read the LP Store purchases", e);
            }
        }
    }

    public void linkPayment(long purchaseId, long journalEntryId, Instant paidAt) {
        database.transaction("Failed to link the LP Store purchase to its payment", connection -> {
            try (PreparedStatement ps = connection.prepareStatement("""
                    UPDATE lp_purchase SET journal_entry_id = ?, purchased_at = ?
                    WHERE purchase_id = ? AND journal_entry_id IS NULL
                    """)) {
                ps.setLong(1, journalEntryId);
                ps.setString(2, paidAt.toString());
                ps.setLong(3, purchaseId);
                ps.executeUpdate();
            }
        });
    }

    public void ignorePayment(long journalEntryId) {
        database.transaction("Failed to ignore the LP Store payment", connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR IGNORE INTO lp_payment_ignored(entry_id) VALUES (?)")) {
                ps.setLong(1, journalEntryId);
                ps.executeUpdate();
            }
        });
    }

    public Set<Long> handledPayments() {
        synchronized (database) {
            try (PreparedStatement ps = database.connection().prepareStatement("""
                    SELECT entry_id FROM lp_payment_ignored
                    UNION SELECT journal_entry_id FROM lp_purchase WHERE journal_entry_id IS NOT NULL
                    """);
                 ResultSet rs = ps.executeQuery()) {
                Set<Long> result = new HashSet<>();
                while (rs.next()) {
                    result.add(rs.getLong(1));
                }
                return result;
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to read the handled LP Store payments", e);
            }
        }
    }

    private static void bind(PreparedStatement ps, LpPurchase purchase) throws SQLException {
        ps.setLong(1, purchase.characterId());
        ps.setLong(2, purchase.corporationId());
        ps.setString(3, purchase.corporationName());
        ps.setLong(4, purchase.offerId());
        ps.setInt(5, purchase.typeId());
        ps.setString(6, purchase.typeName());
        ps.setInt(7, purchase.offers());
        ps.setLong(8, purchase.units());
        ps.setLong(9, purchase.lpSpent());
        ps.setDouble(10, purchase.iskSpent());
        ps.setDouble(11, purchase.otherCost());
        JdbcUtil.setNullable(ps, 12, purchase.targetIskPerLp());
        ps.setString(13, purchase.purchasedAt().toString());
        JdbcUtil.setNullable(ps, 14, purchase.journalEntryId());
        ps.setLong(15, purchase.usedUnits());
        ps.setString(16, purchase.notes());
    }
}
