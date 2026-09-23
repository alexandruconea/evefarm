package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.db.JdbcUtil;
import com.evefarm.model.JournalEntry;
import com.evefarm.model.JournalPayout;
import com.evefarm.model.JournalRow;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class WalletJournalDao {

    private final Database database;

    public WalletJournalDao(Database database) {
        this.database = database;
    }

    public void saveForCharacter(long characterId, List<JournalEntry> entries) {
        synchronized (database) {
            Connection connection = database.connection();
            String insertSql = """
                    INSERT INTO wallet_journal_entry(
                      character_id, entry_id, date, ref_type, amount, balance, description, reason,
                      first_party_id, second_party_id, tax, tax_receiver_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(character_id, entry_id) DO UPDATE SET
                      date = excluded.date,
                      ref_type = excluded.ref_type,
                      amount = excluded.amount,
                      balance = excluded.balance,
                      description = excluded.description,
                      reason = excluded.reason,
                      first_party_id = excluded.first_party_id,
                      second_party_id = excluded.second_party_id,
                      tax = excluded.tax,
                      tax_receiver_id = excluded.tax_receiver_id
                    """;
            try {
                connection.setAutoCommit(false);
                try (PreparedStatement ps = connection.prepareStatement(insertSql)) {
                    for (JournalEntry entry : entries) {
                        ps.setLong(1, characterId);
                        ps.setLong(2, entry.entryId());
                        ps.setString(3, entry.date());
                        ps.setString(4, entry.refType());
                        ps.setDouble(5, entry.amount());
                        ps.setDouble(6, entry.balance());
                        ps.setString(7, entry.description());
                        ps.setString(8, entry.reason());
                        JdbcUtil.setNullable(ps, 9, entry.firstPartyId());
                        JdbcUtil.setNullable(ps, 10, entry.secondPartyId());
                        ps.setDouble(11, entry.tax());
                        JdbcUtil.setNullable(ps, 12, entry.taxReceiverId());
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
                throw new IllegalStateException("Failed to save wallet journal for character " + characterId, e);
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
            }
        }
    }

    public List<JournalRow> listRows(Set<Long> characterIdFilter) {
        StringBuilder sql = new StringBuilder("""
                SELECT j.character_id, c.character_name, j.date, j.ref_type, j.amount, j.balance,
                       j.description, j.first_party_id, j.second_party_id,
                       CASE WHEN j.first_party_id IS NULL THEN NULL
                            ELSE COALESCE(fp.name, '#' || j.first_party_id) END AS first_party_name,
                       CASE WHEN j.second_party_id IS NULL THEN NULL
                            ELSE COALESCE(sp.name, '#' || j.second_party_id) END AS second_party_name
                FROM wallet_journal_entry j
                JOIN characters c ON c.character_id = j.character_id
                LEFT JOIN entity_name_cache fp ON fp.entity_id = j.first_party_id
                LEFT JOIN entity_name_cache sp ON sp.entity_id = j.second_party_id
                WHERE c.removed_at IS NULL
                """);
        if (characterIdFilter != null && !characterIdFilter.isEmpty()) {
            String placeholders = characterIdFilter.stream().map(id -> "?").collect(Collectors.joining(","));
            sql.append(" AND j.character_id IN (").append(placeholders).append(")");
        }
        sql.append(" ORDER BY j.date DESC");

        synchronized (database) {
            Connection connection = database.connection();
            List<JournalRow> result = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
                if (characterIdFilter != null && !characterIdFilter.isEmpty()) {
                    int index = 1;
                    for (Long id : characterIdFilter) {
                        ps.setLong(index++, id);
                    }
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(new JournalRow(
                                rs.getLong("character_id"),
                                rs.getString("character_name"),
                                rs.getString("date"),
                                rs.getString("ref_type"),
                                rs.getDouble("amount"),
                                rs.getDouble("balance"),
                                rs.getString("description"),
                                rs.getString("first_party_name"),
                                rs.getString("second_party_name")
                        ));
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to list wallet journal rows", e);
            }
            return result;
        }
    }

    public record DatedAmount(String date, double amount) {
    }

    public List<JournalPayout> findBountyPayouts(long characterId, Instant from, Instant to) {
        String sql = """
                SELECT date, amount, reason, description FROM wallet_journal_entry
                WHERE character_id = ? AND ref_type = 'bounty_prizes' AND date BETWEEN ? AND ?
                ORDER BY date
                """;
        synchronized (database) {
            Connection connection = database.connection();
            List<JournalPayout> result = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, characterId);
                ps.setString(2, from.toString());
                ps.setString(3, to.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(new JournalPayout(Instant.parse(rs.getString("date")), rs.getDouble("amount"),
                                rs.getString("reason"), rs.getString("description")));
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to read bounty payouts for " + characterId, e);
            }
            return result;
        }
    }

    public List<DatedAmount> findByRefType(long characterId, String refType) {
        String sql = "SELECT date, amount FROM wallet_journal_entry WHERE character_id = ? AND ref_type = ?";
        synchronized (database) {
            Connection connection = database.connection();
            List<DatedAmount> result = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, characterId);
                ps.setString(2, refType);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(new DatedAmount(rs.getString("date"), rs.getDouble("amount")));
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to read " + refType + " journal entries for " + characterId, e);
            }
            return result;
        }
    }
}
