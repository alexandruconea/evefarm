package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.db.JdbcUtil;
import com.evefarm.model.IndustryJobEntry;
import com.evefarm.model.IndustryJobRow;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class IndustryJobDao {

    private final Database database;

    public IndustryJobDao(Database database) {
        this.database = database;
    }

    public void saveForCharacter(long characterId, List<IndustryJobEntry> entries) {
        String deleteSql = """
                DELETE FROM industry_job
                WHERE character_id = ? AND (status IS NULL OR status NOT IN ('delivered', 'cancelled', 'reverted'))
                """;
        String insertSql = """
                INSERT INTO industry_job(
                  character_id, job_id, activity_id, status, blueprint_type_id, product_type_id,
                  runs, cost, facility_id, output_location_id, start_date, end_date)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(character_id, job_id) DO UPDATE SET
                  activity_id = excluded.activity_id, status = excluded.status,
                  blueprint_type_id = excluded.blueprint_type_id, product_type_id = excluded.product_type_id,
                  runs = excluded.runs, cost = excluded.cost, facility_id = excluded.facility_id,
                  output_location_id = excluded.output_location_id, start_date = excluded.start_date,
                  end_date = excluded.end_date
                """;
        database.transaction("Failed to save industry jobs for character " + characterId, connection -> {
            try (PreparedStatement del = connection.prepareStatement(deleteSql)) {
                del.setLong(1, characterId);
                del.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(insertSql)) {
                for (IndustryJobEntry entry : entries) {
                    ps.setLong(1, characterId);
                    ps.setLong(2, entry.jobId());
                    ps.setInt(3, entry.activityId());
                    ps.setString(4, entry.status());
                    ps.setInt(5, entry.blueprintTypeId());
                    JdbcUtil.setNullable(ps, 6, entry.productTypeId());
                    JdbcUtil.setNullable(ps, 7, entry.runs());
                    JdbcUtil.setNullable(ps, 8, entry.cost());
                    JdbcUtil.setNullable(ps, 9, entry.facilityId());
                    JdbcUtil.setNullable(ps, 10, entry.outputLocationId());
                    ps.setString(11, entry.startDate());
                    ps.setString(12, entry.endDate());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        });
    }

    public List<IndustryJobRow> listRows(Set<Long> characterIdFilter) {
        StringBuilder sql = new StringBuilder("""
                SELECT j.character_id, c.character_name, j.job_id, j.activity_id, j.status,
                       COALESCE(bp.name, 'Type #' || j.blueprint_type_id) AS blueprint_name,
                       CASE WHEN j.product_type_id IS NULL THEN NULL
                            ELSE COALESCE(pr.name, 'Type #' || j.product_type_id) END AS product_name,
                       j.runs, j.cost,
                       CASE WHEN j.facility_id IS NULL THEN NULL
                            ELSE COALESCE(l.name, 'Location #' || j.facility_id) END AS facility_name,
                       j.start_date, j.end_date
                FROM industry_job j
                JOIN characters c ON c.character_id = j.character_id
                LEFT JOIN type_cache bp ON bp.type_id = j.blueprint_type_id
                LEFT JOIN type_cache pr ON pr.type_id = j.product_type_id
                LEFT JOIN location_cache l ON l.location_id = j.facility_id
                WHERE c.removed_at IS NULL
                """);
        if (characterIdFilter != null && !characterIdFilter.isEmpty()) {
            String placeholders = characterIdFilter.stream().map(id -> "?").collect(Collectors.joining(","));
            sql.append(" AND j.character_id IN (").append(placeholders).append(")");
        }
        sql.append(" ORDER BY j.end_date DESC");

        synchronized (database) {
            Connection connection = database.connection();
            List<IndustryJobRow> result = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
                if (characterIdFilter != null && !characterIdFilter.isEmpty()) {
                    int index = 1;
                    for (Long id : characterIdFilter) {
                        ps.setLong(index++, id);
                    }
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(new IndustryJobRow(
                                rs.getLong("character_id"),
                                rs.getString("character_name"),
                                rs.getLong("job_id"),
                                rs.getInt("activity_id"),
                                rs.getString("status"),
                                rs.getString("blueprint_name"),
                                rs.getString("product_name"),
                                JdbcUtil.getNullableInt(rs, "runs"),
                                JdbcUtil.getNullableDouble(rs, "cost"),
                                rs.getString("facility_name"),
                                rs.getString("start_date"),
                                rs.getString("end_date")
                        ));
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to list industry job rows", e);
            }
            return result;
        }
    }
}
