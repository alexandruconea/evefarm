package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.db.JdbcUtil;
import com.evefarm.model.TransactionEntry;
import com.evefarm.model.TransactionRow;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class WalletTransactionDao {

    private final Database database;

    public WalletTransactionDao(Database database) {
        this.database = database;
    }

    public void saveForCharacter(long characterId, List<TransactionEntry> entries) {
        synchronized (database) {
            Connection connection = database.connection();
            String insertSql = """
                    INSERT INTO wallet_transaction(
                      character_id, transaction_id, date, type_id, quantity, price, client_id,
                      location_id, is_buy, is_personal, journal_ref_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(character_id, transaction_id) DO UPDATE SET
                      date = excluded.date,
                      type_id = excluded.type_id,
                      quantity = excluded.quantity,
                      price = excluded.price,
                      client_id = excluded.client_id,
                      location_id = excluded.location_id,
                      is_buy = excluded.is_buy,
                      is_personal = excluded.is_personal,
                      journal_ref_id = excluded.journal_ref_id
                    """;
            try {
                connection.setAutoCommit(false);
                try (PreparedStatement ps = connection.prepareStatement(insertSql)) {
                    for (TransactionEntry entry : entries) {
                        ps.setLong(1, characterId);
                        ps.setLong(2, entry.transactionId());
                        ps.setString(3, entry.date());
                        ps.setInt(4, entry.typeId());
                        ps.setLong(5, entry.quantity());
                        ps.setDouble(6, entry.price());
                        JdbcUtil.setNullable(ps, 7, entry.clientId());
                        ps.setLong(8, entry.locationId());
                        ps.setInt(9, entry.isBuy() ? 1 : 0);
                        ps.setInt(10, entry.isPersonal() ? 1 : 0);
                        ps.setLong(11, entry.journalRefId());
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
                throw new IllegalStateException("Failed to save wallet transactions for character " + characterId, e);
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
            }
        }
    }

    public List<TransactionRow> listRows(Set<Long> characterIdFilter) {
        StringBuilder sql = new StringBuilder("""
                SELECT t.character_id, c.character_name, t.date, t.type_id,
                       COALESCE(ty.name, 'Type #' || t.type_id) AS type_name,
                       ty.group_name, t.quantity, t.price,
                       CASE WHEN t.client_id IS NULL THEN NULL
                            ELSE COALESCE(en.name, '#' || t.client_id) END AS client_name,
                       COALESCE(l.name, 'Location #' || t.location_id) AS location_name,
                       t.is_buy
                FROM wallet_transaction t
                JOIN characters c ON c.character_id = t.character_id
                LEFT JOIN type_cache ty ON ty.type_id = t.type_id
                LEFT JOIN location_cache l ON l.location_id = t.location_id
                LEFT JOIN entity_name_cache en ON en.entity_id = t.client_id
                WHERE c.removed_at IS NULL
                """);
        if (characterIdFilter != null && !characterIdFilter.isEmpty()) {
            String placeholders = characterIdFilter.stream().map(id -> "?").collect(Collectors.joining(","));
            sql.append(" AND t.character_id IN (").append(placeholders).append(")");
        }
        sql.append(" ORDER BY t.date DESC");

        synchronized (database) {
            Connection connection = database.connection();
            List<TransactionRow> result = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
                if (characterIdFilter != null && !characterIdFilter.isEmpty()) {
                    int index = 1;
                    for (Long id : characterIdFilter) {
                        ps.setLong(index++, id);
                    }
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(new TransactionRow(
                                rs.getLong("character_id"),
                                rs.getString("character_name"),
                                rs.getString("date"),
                                rs.getInt("type_id"),
                                rs.getString("type_name"),
                                rs.getString("group_name"),
                                rs.getLong("quantity"),
                                rs.getDouble("price"),
                                rs.getString("client_name"),
                                rs.getString("location_name"),
                                rs.getInt("is_buy") != 0
                        ));
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to list wallet transaction rows", e);
            }
            return result;
        }
    }
}
