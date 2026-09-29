package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.db.JdbcUtil;
import com.evefarm.model.ContractEntry;
import com.evefarm.model.ContractItemEntry;
import com.evefarm.model.ContractRow;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class ContractDao {

    private static final String ITEMS_SAVED = "saved";
    private static final String ITEMS_UNAVAILABLE = "unavailable";

    private final Database database;

    public ContractDao(Database database) {
        this.database = database;
    }

    public void saveForCharacter(long characterId, List<ContractEntry> entries) {
        synchronized (database) {
            Connection connection = database.connection();
            String insertSql = """
                    INSERT INTO character_contract(
                      character_id, contract_id, type, status, title, collateral, price, reward, volume,
                      date_issued, date_expired, date_completed, for_corporation, issuer_id, assignee_id,
                      acceptor_id, start_location_id, end_location_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(character_id, contract_id) DO UPDATE SET
                      type = excluded.type, status = excluded.status, title = excluded.title,
                      collateral = excluded.collateral, price = excluded.price, reward = excluded.reward,
                      volume = excluded.volume, date_issued = excluded.date_issued,
                      date_expired = excluded.date_expired, date_completed = excluded.date_completed,
                      for_corporation = excluded.for_corporation, issuer_id = excluded.issuer_id,
                      assignee_id = excluded.assignee_id, acceptor_id = excluded.acceptor_id,
                      start_location_id = excluded.start_location_id, end_location_id = excluded.end_location_id
                    """;
            try {
                connection.setAutoCommit(false);
                try (PreparedStatement ps = connection.prepareStatement(insertSql)) {
                    for (ContractEntry entry : entries) {
                        ps.setLong(1, characterId);
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
                        ps.setInt(13, entry.forCorporation() ? 1 : 0);
                        JdbcUtil.setNullable(ps, 14, entry.issuerId());
                        JdbcUtil.setNullable(ps, 15, entry.assigneeId());
                        JdbcUtil.setNullable(ps, 16, entry.acceptorId());
                        JdbcUtil.setNullable(ps, 17, entry.startLocationId());
                        JdbcUtil.setNullable(ps, 18, entry.endLocationId());
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
                throw new IllegalStateException("Failed to save contracts for character " + characterId, e);
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
            }
        }
    }

    public List<Long> contractsWithoutItems(long characterId) {
        String sql = """
                SELECT contract_id FROM character_contract
                WHERE character_id = ? AND items_status IS NULL
                ORDER BY date_issued DESC
                """;
        synchronized (database) {
            List<Long> result = new ArrayList<>();
            try (PreparedStatement ps = database.connection().prepareStatement(sql)) {
                ps.setLong(1, characterId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(rs.getLong(1));
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to list contracts without items for character "
                        + characterId, e);
            }
            return result;
        }
    }

    public void saveItems(long characterId, long contractId, List<ContractItemEntry> items) {
        synchronized (database) {
            Connection connection = database.connection();
            try {
                connection.setAutoCommit(false);
                try (PreparedStatement del = connection.prepareStatement(
                        "DELETE FROM character_contract_item WHERE character_id = ? AND contract_id = ?")) {
                    del.setLong(1, characterId);
                    del.setLong(2, contractId);
                    del.executeUpdate();
                }
                try (PreparedStatement ps = connection.prepareStatement("""
                        INSERT INTO character_contract_item(
                          character_id, contract_id, record_id, type_id, quantity, raw_quantity, is_included)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """)) {
                    for (ContractItemEntry item : items) {
                        ps.setLong(1, characterId);
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
                setItemsStatus(connection, characterId, contractId, ITEMS_SAVED);
                connection.commit();
            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException ignored) {
                }
                throw new IllegalStateException("Failed to save the items of contract " + contractId, e);
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
            }
        }
    }

    public void markItemsUnavailable(long characterId, long contractId) {
        synchronized (database) {
            try {
                setItemsStatus(database.connection(), characterId, contractId, ITEMS_UNAVAILABLE);
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to mark the items of contract " + contractId, e);
            }
        }
    }

    public Optional<List<ContractItemEntry>> findItems(long characterId, long contractId) {
        synchronized (database) {
            Connection connection = database.connection();
            try {
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT items_status FROM character_contract WHERE character_id = ? AND contract_id = ?")) {
                    ps.setLong(1, characterId);
                    ps.setLong(2, contractId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next() || !ITEMS_SAVED.equals(rs.getString(1))) {
                            return Optional.empty();
                        }
                    }
                }
                List<ContractItemEntry> items = new ArrayList<>();
                try (PreparedStatement ps = connection.prepareStatement("""
                        SELECT record_id, type_id, quantity, raw_quantity, is_included
                        FROM character_contract_item
                        WHERE character_id = ? AND contract_id = ?
                        ORDER BY record_id
                        """)) {
                    ps.setLong(1, characterId);
                    ps.setLong(2, contractId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            items.add(new ContractItemEntry(rs.getLong("record_id"), rs.getInt("type_id"),
                                    rs.getLong("quantity"), JdbcUtil.getNullableInt(rs, "raw_quantity"),
                                    rs.getInt("is_included") != 0));
                        }
                    }
                }
                return Optional.of(items);
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to read the items of contract " + contractId, e);
            }
        }
    }

    private static void setItemsStatus(Connection connection, long characterId, long contractId, String status)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE character_contract SET items_status = ? WHERE character_id = ? AND contract_id = ?")) {
            ps.setString(1, status);
            ps.setLong(2, characterId);
            ps.setLong(3, contractId);
            ps.executeUpdate();
        }
    }

    public List<ContractRow> listRows(Set<Long> characterIdFilter) {
        StringBuilder sql = new StringBuilder("""
                SELECT ct.character_id, c.character_name, ct.contract_id, ct.type, ct.status, ct.title,
                       ct.collateral, ct.price, ct.reward, ct.volume,
                       ct.date_issued, ct.date_expired, ct.date_completed,
                       ct.issuer_id, ct.assignee_id, ct.acceptor_id, ct.start_location_id, ct.end_location_id,
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
                FROM character_contract ct
                JOIN characters c ON c.character_id = ct.character_id
                LEFT JOIN entity_name_cache iss ON iss.entity_id = ct.issuer_id
                LEFT JOIN entity_name_cache asg ON asg.entity_id = ct.assignee_id
                LEFT JOIN entity_name_cache acc ON acc.entity_id = ct.acceptor_id
                LEFT JOIN location_cache sl ON sl.location_id = ct.start_location_id
                LEFT JOIN location_cache el ON el.location_id = ct.end_location_id
                WHERE c.removed_at IS NULL
                """);
        if (characterIdFilter != null && !characterIdFilter.isEmpty()) {
            String placeholders = characterIdFilter.stream().map(id -> "?").collect(Collectors.joining(","));
            sql.append(" AND ct.character_id IN (").append(placeholders).append(")");
        }
        sql.append(" ORDER BY ct.date_issued DESC");

        synchronized (database) {
            Connection connection = database.connection();
            List<ContractRow> result = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
                if (characterIdFilter != null && !characterIdFilter.isEmpty()) {
                    int index = 1;
                    for (Long id : characterIdFilter) {
                        ps.setLong(index++, id);
                    }
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(new ContractRow(
                                rs.getLong("character_id"),
                                rs.getString("character_name"),
                                rs.getLong("contract_id"),
                                rs.getString("type"),
                                rs.getString("status"),
                                rs.getString("title"),
                                JdbcUtil.getNullableDouble(rs, "collateral"),
                                JdbcUtil.getNullableDouble(rs, "price"),
                                JdbcUtil.getNullableDouble(rs, "reward"),
                                JdbcUtil.getNullableDouble(rs, "volume"),
                                rs.getString("date_issued"),
                                rs.getString("date_expired"),
                                rs.getString("date_completed"),
                                rs.getString("issuer_name"),
                                rs.getString("assignee_name"),
                                rs.getString("acceptor_name"),
                                rs.getString("start_location_name"),
                                rs.getString("end_location_name")
                        ));
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to list contract rows", e);
            }
            return result;
        }
    }

}
