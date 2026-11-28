package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.db.JdbcUtil;
import com.evefarm.model.AssetEntry;
import com.evefarm.model.AssetRow;
import com.evefarm.model.Corporation;
import com.evefarm.model.CorporationAccess;
import com.evefarm.model.JournalEntry;
import com.evefarm.model.JournalRow;
import com.evefarm.model.TransactionEntry;
import com.evefarm.model.TransactionRow;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class CorporationDao {

    public static final String HANGAR = "hangar";
    public static final String WALLET = "wallet";
    private static final String MASTER_WALLET = "Master Wallet";

    public record Wallet(int division, String name, double balance) {
    }

    private final Database database;

    public CorporationDao(Database database) {
        this.database = database;
    }

    public void saveCorporation(Corporation corporation) {
        String sql = """
                INSERT INTO corporation(corporation_id, name, ticker) VALUES (?, ?, ?)
                ON CONFLICT(corporation_id) DO UPDATE SET name = excluded.name, ticker = excluded.ticker
                """;
        database.transaction("Failed to save corporation " + corporation.corporationId(), connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, corporation.corporationId());
                ps.setString(2, corporation.name());
                ps.setString(3, corporation.ticker());
                ps.executeUpdate();
            }
        });
    }

    public List<Corporation> listCorporations() {
        String sql = "SELECT corporation_id, name, ticker FROM corporation ORDER BY name";
        return query(sql, "the corporations", rs -> new Corporation(rs.getLong("corporation_id"),
                rs.getString("name"), rs.getString("ticker")));
    }

    public void saveAccess(CorporationAccess access) {
        String sql = """
                INSERT INTO corporation_access(character_id, corporation_id, roles, checked_at) VALUES (?, ?, ?, ?)
                ON CONFLICT(character_id) DO UPDATE SET
                  corporation_id = excluded.corporation_id, roles = excluded.roles, checked_at = excluded.checked_at
                """;
        database.transaction("Failed to save the corporation roles of " + access.characterId(), connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, access.characterId());
                ps.setLong(2, access.corporationId());
                ps.setString(3, String.join(",", access.roles()));
                ps.setString(4, access.checkedAt().toString());
                ps.executeUpdate();
            }
        });
    }

    public List<CorporationAccess> listAccess() {
        String sql = """
                SELECT a.character_id, a.corporation_id, a.roles, a.checked_at FROM corporation_access a
                JOIN characters c ON c.character_id = a.character_id
                WHERE c.removed_at IS NULL
                """;
        return query(sql, "the corporation roles", rs -> new CorporationAccess(rs.getLong("character_id"),
                rs.getLong("corporation_id"), roles(rs.getString("roles")), Instant.parse(rs.getString("checked_at"))));
    }

    public void saveDivisions(long corporationId, String kind, Map<Integer, String> names) {
        database.transaction("Failed to save the divisions of corporation " + corporationId, connection -> {
            try (PreparedStatement del = connection.prepareStatement(
                    "DELETE FROM corporation_division WHERE corporation_id = ? AND kind = ?")) {
                del.setLong(1, corporationId);
                del.setString(2, kind);
                del.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO corporation_division(corporation_id, kind, division, name) VALUES (?, ?, ?, ?)")) {
                for (Map.Entry<Integer, String> division : names.entrySet()) {
                    ps.setLong(1, corporationId);
                    ps.setString(2, kind);
                    ps.setInt(3, division.getKey());
                    ps.setString(4, division.getValue());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        });
    }

    public Map<Integer, String> divisionNames(long corporationId, String kind) {
        Map<Integer, String> names = new HashMap<>();
        String sql = "SELECT division, name FROM corporation_division WHERE corporation_id = ? AND kind = ?";
        synchronized (database) {
            try (PreparedStatement ps = database.connection().prepareStatement(sql)) {
                ps.setLong(1, corporationId);
                ps.setString(2, kind);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        names.put(rs.getInt("division"), rs.getString("name"));
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to read the divisions of corporation " + corporationId, e);
            }
        }
        return names;
    }

    public static String walletName(int division, String name) {
        if (name != null && !name.isBlank()) {
            return name;
        }
        return division == 1 ? MASTER_WALLET : "Wallet " + division;
    }

    public void saveWallets(long corporationId, Map<Integer, Double> balances) {
        String now = Instant.now().toString();
        database.transaction("Failed to save the wallets of corporation " + corporationId, connection -> {
            try (PreparedStatement del = connection.prepareStatement(
                    "DELETE FROM corporation_wallet WHERE corporation_id = ?")) {
                del.setLong(1, corporationId);
                del.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement("INSERT INTO corporation_wallet(corporation_id, "
                    + "division, balance, updated_at) VALUES (?, ?, ?, ?)")) {
                for (Map.Entry<Integer, Double> wallet : balances.entrySet()) {
                    ps.setLong(1, corporationId);
                    ps.setInt(2, wallet.getKey());
                    ps.setDouble(3, wallet.getValue());
                    ps.setString(4, now);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        });
    }

    public List<Wallet> wallets(long corporationId) {
        Map<Integer, String> names = divisionNames(corporationId, WALLET);
        String sql = "SELECT division, balance FROM corporation_wallet WHERE corporation_id = ? ORDER BY division";
        List<Wallet> wallets = new ArrayList<>();
        synchronized (database) {
            try (PreparedStatement ps = database.connection().prepareStatement(sql)) {
                ps.setLong(1, corporationId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int division = rs.getInt("division");
                        wallets.add(new Wallet(division, walletName(division, names.get(division)),
                                rs.getDouble("balance")));
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to read the wallets of corporation " + corporationId, e);
            }
        }
        return wallets;
    }

    public void saveJournal(long corporationId, int division, List<JournalEntry> entries) {
        String sql = """
                INSERT INTO corporation_journal(corporation_id, division, entry_id, date, ref_type, amount, balance,
                  description, reason, first_party_id, second_party_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(corporation_id, division, entry_id) DO UPDATE SET
                  date = excluded.date, ref_type = excluded.ref_type, amount = excluded.amount,
                  balance = excluded.balance, description = excluded.description, reason = excluded.reason,
                  first_party_id = excluded.first_party_id, second_party_id = excluded.second_party_id
                """;
        database.transaction("Failed to save the journal of corporation " + corporationId, connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                for (JournalEntry entry : entries) {
                    ps.setLong(1, corporationId);
                    ps.setInt(2, division);
                    ps.setLong(3, entry.entryId());
                    ps.setString(4, entry.date());
                    ps.setString(5, entry.refType());
                    ps.setDouble(6, entry.amount());
                    ps.setDouble(7, entry.balance());
                    ps.setString(8, entry.description());
                    ps.setString(9, entry.reason());
                    JdbcUtil.setNullable(ps, 10, entry.firstPartyId());
                    JdbcUtil.setNullable(ps, 11, entry.secondPartyId());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        });
    }

    public void saveTransactions(long corporationId, int division, List<TransactionEntry> entries) {
        String sql = """
                INSERT INTO corporation_transaction(corporation_id, division, transaction_id, date, type_id, quantity,
                  price, client_id, location_id, is_buy, journal_ref_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(corporation_id, division, transaction_id) DO NOTHING
                """;
        database.transaction("Failed to save the transactions of corporation " + corporationId, connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                for (TransactionEntry entry : entries) {
                    ps.setLong(1, corporationId);
                    ps.setInt(2, division);
                    ps.setLong(3, entry.transactionId());
                    ps.setString(4, entry.date());
                    ps.setInt(5, entry.typeId());
                    ps.setLong(6, entry.quantity());
                    ps.setDouble(7, entry.price());
                    JdbcUtil.setNullable(ps, 8, entry.clientId());
                    ps.setLong(9, entry.locationId());
                    ps.setInt(10, entry.isBuy() ? 1 : 0);
                    ps.setLong(11, entry.journalRefId());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        });
    }

    public void replaceAssets(long corporationId, List<AssetEntry> entries) {
        String insertSql = """
                INSERT INTO corporation_asset(corporation_id, item_id, type_id, quantity, location_id, location_flag,
                  is_singleton, is_blueprint_copy, name, container_name, unit_price, total_value, fetched_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        String now = Instant.now().toString();
        database.transaction("Failed to replace the assets of corporation " + corporationId, connection -> {
            try (PreparedStatement del = connection.prepareStatement(
                    "DELETE FROM corporation_asset WHERE corporation_id = ?")) {
                del.setLong(1, corporationId);
                del.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(insertSql)) {
                for (AssetEntry entry : entries) {
                    ps.setLong(1, corporationId);
                    ps.setLong(2, entry.itemId());
                    ps.setInt(3, entry.typeId());
                    ps.setLong(4, entry.quantity());
                    JdbcUtil.setNullable(ps, 5, entry.locationId());
                    ps.setString(6, entry.locationFlag());
                    ps.setInt(7, entry.isSingleton() ? 1 : 0);
                    ps.setInt(8, entry.blueprintCopy() ? 1 : 0);
                    ps.setString(9, entry.name());
                    ps.setString(10, entry.containerName());
                    ps.setDouble(11, entry.unitPrice());
                    ps.setDouble(12, entry.totalValue());
                    ps.setString(13, now);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        });
    }

    public Map<Long, String> assetNames(long corporationId) {
        Map<Long, String> names = new HashMap<>();
        String sql = "SELECT item_id, name FROM corporation_asset WHERE corporation_id = ? AND name IS NOT NULL";
        synchronized (database) {
            try (PreparedStatement ps = database.connection().prepareStatement(sql)) {
                ps.setLong(1, corporationId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        names.put(rs.getLong("item_id"), rs.getString("name"));
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to read the asset names of corporation " + corporationId, e);
            }
        }
        return names;
    }

    public double assetsValue(long corporationId) {
        return sum("SELECT COALESCE(SUM(total_value), 0) FROM corporation_asset WHERE corporation_id = ?",
                corporationId);
    }

    public double walletsValue(long corporationId) {
        return sum("SELECT COALESCE(SUM(balance), 0) FROM corporation_wallet WHERE corporation_id = ?", corporationId);
    }

    public List<AssetRow> listAssetRows() {
        return assetRows(null, null, false);
    }

    public Optional<AssetRow> mostValuable(long corporationId, String categoryName) {
        return assetRows(corporationId, categoryName, true).stream().findFirst();
    }

    private List<AssetRow> assetRows(Long corporationId, String categoryName, boolean mostValuableOnly) {
        StringBuilder sql = new StringBuilder("""
                SELECT a.item_id, a.corporation_id, co.name AS corporation_name, co.ticker, a.type_id,
                       COALESCE(t.name, 'Type #' || a.type_id)
                           || CASE WHEN a.is_blueprint_copy = 1 THEN ' (Blueprint Copy)' ELSE '' END AS type_name,
                       t.group_name, t.category_name, a.quantity,
                       COALESCE(l.name, CASE WHEN a.location_id IS NULL THEN 'Unknown'
                                             ELSE 'Location #' || a.location_id END) AS location_name,
                       a.container_name,
                       CASE WHEN a.location_flag LIKE 'CorpSAG%'
                            THEN 'Hangar ' || substr(a.location_flag, 8) || COALESCE(': ' || d.name, '')
                            ELSE a.location_flag END AS location_flag,
                       a.is_singleton, t.volume, a.unit_price, a.total_value
                FROM corporation_asset a
                JOIN corporation co ON co.corporation_id = a.corporation_id
                LEFT JOIN type_cache t ON t.type_id = a.type_id
                LEFT JOIN location_cache l ON l.location_id = a.location_id
                LEFT JOIN corporation_division d ON d.corporation_id = a.corporation_id AND d.kind = 'hangar'
                     AND a.location_flag LIKE 'CorpSAG%' AND d.division = CAST(substr(a.location_flag, 8) AS INTEGER)
                WHERE 1 = 1
                """);
        if (corporationId != null) {
            sql.append(" AND a.corporation_id = ?");
        }
        if (categoryName != null) {
            sql.append(" AND t.category_name = ?");
        }
        sql.append(mostValuableOnly ? " ORDER BY a.unit_price DESC, a.total_value DESC LIMIT 1"
                : " ORDER BY a.total_value DESC");
        synchronized (database) {
            List<AssetRow> rows = new ArrayList<>();
            try (PreparedStatement ps = database.connection().prepareStatement(sql.toString())) {
                int index = 1;
                if (corporationId != null) {
                    ps.setLong(index++, corporationId);
                }
                if (categoryName != null) {
                    ps.setString(index, categoryName);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rows.add(new AssetRow(rs.getLong("item_id"), rs.getLong("corporation_id"),
                                label(rs.getString("corporation_name"), rs.getString("ticker")), rs.getInt("type_id"),
                                rs.getString("type_name"), rs.getString("group_name"), rs.getString("category_name"),
                                rs.getLong("quantity"), rs.getString("location_name"), rs.getString("container_name"),
                                rs.getString("location_flag"), rs.getInt("is_singleton") == 1, rs.getDouble("volume"),
                                rs.getDouble("unit_price"), rs.getDouble("total_value"), true));
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to list the corporation assets", e);
            }
            return rows;
        }
    }

    public List<JournalRow> listJournalRows() {
        String sql = """
                SELECT j.corporation_id, co.name AS corporation_name, co.ticker, j.division, d.name AS division_name,
                       j.date, j.ref_type, j.amount, j.balance, j.description, j.first_party_id, j.second_party_id,
                       CASE WHEN j.first_party_id IS NULL THEN NULL
                            ELSE COALESCE(fp.name, '#' || j.first_party_id) END AS first_party_name,
                       CASE WHEN j.second_party_id IS NULL THEN NULL
                            ELSE COALESCE(sp.name, '#' || j.second_party_id) END AS second_party_name,
                       fp.category AS first_party_category, sp.category AS second_party_category
                FROM corporation_journal j
                JOIN corporation co ON co.corporation_id = j.corporation_id
                LEFT JOIN corporation_division d ON d.corporation_id = j.corporation_id AND d.kind = 'wallet'
                     AND d.division = j.division
                LEFT JOIN entity_name_cache fp ON fp.entity_id = j.first_party_id
                LEFT JOIN entity_name_cache sp ON sp.entity_id = j.second_party_id
                ORDER BY j.date DESC
                """;
        return query(sql, "the corporation journal", rs -> new JournalRow(rs.getLong("corporation_id"),
                owner(rs), rs.getString("date"), rs.getString("ref_type"), rs.getDouble("amount"),
                rs.getDouble("balance"), rs.getString("description"),
                new JournalRow.Party(JdbcUtil.getNullableLong(rs, "first_party_id"), rs.getString("first_party_name"),
                        rs.getString("first_party_category")),
                new JournalRow.Party(JdbcUtil.getNullableLong(rs, "second_party_id"),
                        rs.getString("second_party_name"), rs.getString("second_party_category")), true));
    }

    public List<TransactionRow> listTransactionRows() {
        String sql = """
                SELECT t.corporation_id, co.name AS corporation_name, co.ticker, t.division, d.name AS division_name,
                       t.date, t.type_id, COALESCE(ty.name, 'Type #' || t.type_id) AS type_name, ty.group_name,
                       t.quantity, t.price,
                       CASE WHEN t.client_id IS NULL THEN NULL
                            ELSE COALESCE(en.name, '#' || t.client_id) END AS client_name,
                       COALESCE(l.name, 'Location #' || t.location_id) AS location_name, t.is_buy
                FROM corporation_transaction t
                JOIN corporation co ON co.corporation_id = t.corporation_id
                LEFT JOIN corporation_division d ON d.corporation_id = t.corporation_id AND d.kind = 'wallet'
                     AND d.division = t.division
                LEFT JOIN type_cache ty ON ty.type_id = t.type_id
                LEFT JOIN location_cache l ON l.location_id = t.location_id
                LEFT JOIN entity_name_cache en ON en.entity_id = t.client_id
                ORDER BY t.date DESC
                """;
        return query(sql, "the corporation transactions", rs -> new TransactionRow(rs.getLong("corporation_id"),
                owner(rs), rs.getString("date"), rs.getInt("type_id"), rs.getString("type_name"),
                rs.getString("group_name"), rs.getLong("quantity"), rs.getDouble("price"), rs.getString("client_name"),
                rs.getString("location_name"), rs.getInt("is_buy") != 0, true));
    }

    private static String owner(ResultSet rs) throws SQLException {
        return label(rs.getString("corporation_name"), rs.getString("ticker")) + " · "
                + walletName(rs.getInt("division"), rs.getString("division_name"));
    }

    private static String label(String name, String ticker) {
        return new Corporation(0, name, ticker).label();
    }

    private static List<String> roles(String text) {
        return text == null || text.isBlank() ? List.of() : Arrays.asList(text.split(","));
    }

    private double sum(String sql, long corporationId) {
        synchronized (database) {
            try (PreparedStatement ps = database.connection().prepareStatement(sql)) {
                ps.setLong(1, corporationId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getDouble(1) : 0;
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to add up corporation " + corporationId, e);
            }
        }
    }

    @FunctionalInterface
    private interface RowReader<T> {
        T read(ResultSet rs) throws SQLException;
    }

    private <T> List<T> query(String sql, String what, RowReader<T> reader) {
        synchronized (database) {
            Connection connection = database.connection();
            List<T> rows = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(reader.read(rs));
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to read " + what, e);
            }
            return rows;
        }
    }
}
