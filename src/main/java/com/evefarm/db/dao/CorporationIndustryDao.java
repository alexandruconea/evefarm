package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.db.JdbcUtil;
import com.evefarm.model.BlueprintEntry;
import com.evefarm.model.BlueprintRow;
import com.evefarm.model.IndustryJobEntry;
import com.evefarm.model.IndustryJobRow;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class CorporationIndustryDao {

    public record Job(IndustryJobEntry entry, Long installerId) {
    }

    private final Database database;

    public CorporationIndustryDao(Database database) {
        this.database = database;
    }

    public void saveJobs(long corporationId, List<Job> jobs) {
        String deleteSql = """
                DELETE FROM corporation_industry_job
                WHERE corporation_id = ? AND (status IS NULL OR status NOT IN ('delivered', 'cancelled', 'reverted'))
                """;
        String insertSql = """
                INSERT INTO corporation_industry_job(
                  corporation_id, job_id, installer_id, activity_id, status, blueprint_type_id, product_type_id,
                  runs, cost, facility_id, output_location_id, start_date, end_date)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(corporation_id, job_id) DO UPDATE SET
                  installer_id = excluded.installer_id, activity_id = excluded.activity_id,
                  status = excluded.status, blueprint_type_id = excluded.blueprint_type_id,
                  product_type_id = excluded.product_type_id, runs = excluded.runs, cost = excluded.cost,
                  facility_id = excluded.facility_id, output_location_id = excluded.output_location_id,
                  start_date = excluded.start_date, end_date = excluded.end_date
                """;
        database.transaction("Failed to save the industry jobs of corporation " + corporationId, connection -> {
            try (PreparedStatement del = connection.prepareStatement(deleteSql)) {
                del.setLong(1, corporationId);
                del.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(insertSql)) {
                for (Job job : jobs) {
                    IndustryJobEntry entry = job.entry();
                    ps.setLong(1, corporationId);
                    ps.setLong(2, entry.jobId());
                    JdbcUtil.setNullable(ps, 3, job.installerId());
                    ps.setInt(4, entry.activityId());
                    ps.setString(5, entry.status());
                    ps.setInt(6, entry.blueprintTypeId());
                    JdbcUtil.setNullable(ps, 7, entry.productTypeId());
                    JdbcUtil.setNullable(ps, 8, entry.runs());
                    JdbcUtil.setNullable(ps, 9, entry.cost());
                    JdbcUtil.setNullable(ps, 10, entry.facilityId());
                    JdbcUtil.setNullable(ps, 11, entry.outputLocationId());
                    ps.setString(12, entry.startDate());
                    ps.setString(13, entry.endDate());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        });
    }

    /** The jobs still running, paused or waiting to be delivered. */
    public List<IndustryJobDao.FinishedJob> listOpenJobs(long corporationId) {
        return Queries.list(database, """
                SELECT corporation_id, end_date, activity_id, blueprint_type_id, product_type_id, runs
                FROM corporation_industry_job
                WHERE corporation_id = ? AND status IN ('active', 'paused', 'ready')
                """, "the open industry jobs of corporation " + corporationId,
                ps -> ps.setLong(1, corporationId), CorporationIndustryDao::job);
    }

    /** The jobs that ended between {@code since} and {@code until}, as {@link IndustryJobDao#findFinished}. */
    public List<IndustryJobDao.FinishedJob> findFinished(Set<Long> corporationIds, Instant since, Instant until) {
        if (corporationIds.isEmpty()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>(corporationIds);
        return Queries.list(database, "SELECT corporation_id, end_date, activity_id, blueprint_type_id, "
                        + "product_type_id, runs FROM corporation_industry_job WHERE corporation_id IN ("
                        + JdbcUtil.placeholders(ids.size()) + ") AND end_date >= ? AND end_date <= ? "
                        + "AND (status IS NULL OR status NOT IN ('cancelled', 'reverted'))",
                "the finished industry jobs of the corporations", ps -> {
                    int index = 1;
                    for (Long id : ids) {
                        ps.setLong(index++, id);
                    }
                    ps.setString(index++, since.toString());
                    ps.setString(index, until.toString());
                }, CorporationIndustryDao::job);
    }

    private static IndustryJobDao.FinishedJob job(ResultSet rs) throws SQLException {
        Integer runs = JdbcUtil.getNullableInt(rs, "runs");
        String end = rs.getString("end_date");
        return new IndustryJobDao.FinishedJob(rs.getLong("corporation_id"), end == null ? null : Instant.parse(end),
                rs.getInt("activity_id"), rs.getInt("blueprint_type_id"),
                JdbcUtil.getNullableInt(rs, "product_type_id"), runs == null ? 1 : runs);
    }

    public List<IndustryJobRow> listJobRows() {
        String sql = """
                SELECT j.corporation_id, co.name AS corporation_name, co.ticker, ins.name AS installer_name,
                       j.job_id, j.activity_id, j.status,
                       COALESCE(bp.name, 'Type #' || j.blueprint_type_id) AS blueprint_name,
                       CASE WHEN j.product_type_id IS NULL THEN NULL
                            ELSE COALESCE(pr.name, 'Type #' || j.product_type_id) END AS product_name,
                       j.runs, j.cost,
                       CASE WHEN j.facility_id IS NULL THEN NULL
                            ELSE COALESCE(l.name, 'Location #' || j.facility_id) END AS facility_name,
                       j.start_date, j.end_date
                FROM corporation_industry_job j
                JOIN corporation co ON co.corporation_id = j.corporation_id
                LEFT JOIN entity_name_cache ins ON ins.entity_id = j.installer_id
                LEFT JOIN type_cache bp ON bp.type_id = j.blueprint_type_id
                LEFT JOIN type_cache pr ON pr.type_id = j.product_type_id
                LEFT JOIN location_cache l ON l.location_id = j.facility_id
                ORDER BY j.end_date DESC
                """;
        return Queries.list(database, sql, "the corporation industry jobs", ps -> {
        }, rs -> {
            String installer = rs.getString("installer_name");
            String owner = Queries.label(rs.getString("corporation_name"), rs.getString("ticker"))
                    + (installer == null ? "" : " · " + installer);
            return new IndustryJobRow(rs.getLong("corporation_id"), owner, rs.getLong("job_id"),
                    rs.getInt("activity_id"), rs.getString("status"), rs.getString("blueprint_name"),
                    rs.getString("product_name"), JdbcUtil.getNullableInt(rs, "runs"),
                    JdbcUtil.getNullableDouble(rs, "cost"), rs.getString("facility_name"), rs.getString("start_date"),
                    rs.getString("end_date"), true);
        });
    }

    public void replaceBlueprints(long corporationId, List<BlueprintEntry> blueprints) {
        String insertSql = """
                INSERT INTO corporation_blueprint(corporation_id, item_id, type_id, location_id, location_flag,
                  quantity, material_efficiency, time_efficiency, runs)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(corporation_id, item_id) DO UPDATE SET
                  type_id = excluded.type_id, location_id = excluded.location_id,
                  location_flag = excluded.location_flag, quantity = excluded.quantity,
                  material_efficiency = excluded.material_efficiency, time_efficiency = excluded.time_efficiency,
                  runs = excluded.runs
                """;
        database.transaction("Failed to save the blueprints of corporation " + corporationId, connection -> {
            try (PreparedStatement del = connection.prepareStatement(
                    "DELETE FROM corporation_blueprint WHERE corporation_id = ?")) {
                del.setLong(1, corporationId);
                del.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(insertSql)) {
                for (BlueprintEntry blueprint : blueprints) {
                    ps.setLong(1, corporationId);
                    ps.setLong(2, blueprint.itemId());
                    ps.setInt(3, blueprint.typeId());
                    JdbcUtil.setNullable(ps, 4, blueprint.locationId());
                    ps.setString(5, blueprint.locationFlag());
                    ps.setInt(6, blueprint.quantity());
                    ps.setInt(7, blueprint.materialEfficiency());
                    ps.setInt(8, blueprint.timeEfficiency());
                    ps.setInt(9, blueprint.runs());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        });
    }

    public List<BlueprintRow> listBlueprintRows() {
        String sql = """
                SELECT b.corporation_id, co.name AS corporation_name, co.ticker, b.item_id, b.type_id,
                       COALESCE(t.name, 'Type #' || b.type_id) AS type_name, t.group_name, b.quantity,
                       b.material_efficiency, b.time_efficiency, b.runs,
                       COALESCE(al.name, l.name, CASE WHEN COALESCE(a.location_id, b.location_id) IS NULL
                                                      THEN 'Unknown'
                                                      ELSE 'Location #' || COALESCE(a.location_id, b.location_id)
                                                 END) AS location_name,
                       a.container_name,
                       CASE WHEN b.location_flag LIKE 'CorpSAG%'
                            THEN 'Hangar ' || substr(b.location_flag, 8) || COALESCE(': ' || d.name, '')
                            ELSE b.location_flag END AS location_flag
                FROM corporation_blueprint b
                JOIN corporation co ON co.corporation_id = b.corporation_id
                LEFT JOIN corporation_asset a ON a.corporation_id = b.corporation_id AND a.item_id = b.item_id
                LEFT JOIN location_cache al ON al.location_id = a.location_id
                LEFT JOIN location_cache l ON l.location_id = b.location_id
                LEFT JOIN type_cache t ON t.type_id = b.type_id
                LEFT JOIN corporation_division d ON d.corporation_id = b.corporation_id AND d.kind = 'hangar'
                     AND b.location_flag LIKE 'CorpSAG%' AND d.division = CAST(substr(b.location_flag, 8) AS INTEGER)
                ORDER BY type_name, b.material_efficiency DESC, b.time_efficiency DESC
                """;
        return Queries.list(database, sql, "the corporation blueprints", ps -> {
        }, rs -> new BlueprintRow(rs.getLong("corporation_id"),
                Queries.label(rs.getString("corporation_name"), rs.getString("ticker")), rs.getLong("item_id"),
                rs.getInt("type_id"), rs.getString("type_name"), rs.getString("group_name"), rs.getInt("quantity"),
                rs.getInt("material_efficiency"), rs.getInt("time_efficiency"), rs.getInt("runs"),
                rs.getString("location_name"), rs.getString("container_name"), rs.getString("location_flag"),
                true));
    }
}
