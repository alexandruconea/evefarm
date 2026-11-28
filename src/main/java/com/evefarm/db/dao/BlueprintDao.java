package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.db.JdbcUtil;
import com.evefarm.model.BlueprintEntry;
import com.evefarm.model.BlueprintRow;

import java.sql.PreparedStatement;
import java.util.List;

public final class BlueprintDao {

    private final Database database;

    public BlueprintDao(Database database) {
        this.database = database;
    }

    public void replaceForCharacter(long characterId, List<BlueprintEntry> blueprints) {
        String insertSql = """
                INSERT INTO character_blueprint(character_id, item_id, type_id, location_id, location_flag,
                  quantity, material_efficiency, time_efficiency, runs)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(character_id, item_id) DO UPDATE SET
                  type_id = excluded.type_id, location_id = excluded.location_id,
                  location_flag = excluded.location_flag, quantity = excluded.quantity,
                  material_efficiency = excluded.material_efficiency, time_efficiency = excluded.time_efficiency,
                  runs = excluded.runs
                """;
        database.transaction("Failed to save the blueprints of character " + characterId, connection -> {
            try (PreparedStatement del = connection.prepareStatement(
                    "DELETE FROM character_blueprint WHERE character_id = ?")) {
                del.setLong(1, characterId);
                del.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(insertSql)) {
                for (BlueprintEntry blueprint : blueprints) {
                    ps.setLong(1, characterId);
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

    public List<BlueprintRow> listRows() {
        String sql = """
                SELECT b.character_id, c.character_name, b.item_id, b.type_id,
                       COALESCE(t.name, 'Type #' || b.type_id) AS type_name, t.group_name, b.quantity,
                       b.material_efficiency, b.time_efficiency, b.runs,
                       COALESCE(al.name, l.name, CASE WHEN COALESCE(a.location_id, b.location_id) IS NULL
                                                      THEN 'Unknown'
                                                      ELSE 'Location #' || COALESCE(a.location_id, b.location_id)
                                                 END) AS location_name,
                       a.container_name, b.location_flag
                FROM character_blueprint b
                JOIN characters c ON c.character_id = b.character_id
                LEFT JOIN asset_current a ON a.character_id = b.character_id AND a.item_id = b.item_id
                LEFT JOIN location_cache al ON al.location_id = a.location_id
                LEFT JOIN location_cache l ON l.location_id = b.location_id
                LEFT JOIN type_cache t ON t.type_id = b.type_id
                WHERE c.removed_at IS NULL
                ORDER BY type_name, b.material_efficiency DESC, b.time_efficiency DESC
                """;
        return Queries.list(database, sql, "the blueprints", ps -> {
        }, rs -> new BlueprintRow(rs.getLong("character_id"), rs.getString("character_name"), rs.getLong("item_id"),
                rs.getInt("type_id"), rs.getString("type_name"), rs.getString("group_name"), rs.getInt("quantity"),
                rs.getInt("material_efficiency"), rs.getInt("time_efficiency"), rs.getInt("runs"),
                rs.getString("location_name"), rs.getString("container_name"), rs.getString("location_flag"),
                false));
    }
}
