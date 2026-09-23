package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.db.JdbcUtil;
import com.evefarm.model.ContractEntry;
import com.evefarm.model.ContractRow;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class ContractDao {

    private final Database database;

    public ContractDao(Database database) {
        this.database = database;
    }

    public void replaceForCharacter(long characterId, List<ContractEntry> entries) {
        synchronized (database) {
            Connection connection = database.connection();
            String deleteSql = "DELETE FROM character_contract WHERE character_id = ?";
            String insertSql = """
                    INSERT INTO character_contract(
                      character_id, contract_id, type, status, title, collateral, price, reward, volume,
                      date_issued, date_expired, date_completed, for_corporation, issuer_id, assignee_id,
                      acceptor_id, start_location_id, end_location_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
            try {
                connection.setAutoCommit(false);
                try (PreparedStatement del = connection.prepareStatement(deleteSql)) {
                    del.setLong(1, characterId);
                    del.executeUpdate();
                }
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
                throw new IllegalStateException("Failed to replace contracts for character " + characterId, e);
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
            }
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
                """);
        if (characterIdFilter != null && !characterIdFilter.isEmpty()) {
            String placeholders = characterIdFilter.stream().map(id -> "?").collect(Collectors.joining(","));
            sql.append(" WHERE ct.character_id IN (").append(placeholders).append(")");
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
