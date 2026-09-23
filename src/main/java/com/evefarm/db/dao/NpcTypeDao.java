package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.model.NpcType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class NpcTypeDao {

    private final Database database;

    public NpcTypeDao(Database database) {
        this.database = database;
    }

    public void replaceAll(List<NpcType> types) {
        synchronized (database) {
            Connection connection = database.connection();
            String insertSql = "INSERT INTO sde_npc_type(type_id, type_name, group_id, group_name) VALUES (?, ?, ?, ?)";
            try {
                connection.setAutoCommit(false);
                try (Statement statement = connection.createStatement()) {
                    statement.execute("DELETE FROM sde_npc_type");
                }
                try (PreparedStatement ps = connection.prepareStatement(insertSql)) {
                    for (NpcType type : types) {
                        ps.setInt(1, type.typeId());
                        ps.setString(2, type.typeName());
                        ps.setInt(3, type.groupId());
                        ps.setString(4, type.groupName());
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
                throw new IllegalStateException("Failed to replace the NPC type catalog", e);
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
            }
        }
    }

    public List<NpcType> listAll() {
        String sql = "SELECT type_id, type_name, group_id, group_name FROM sde_npc_type";
        synchronized (database) {
            Connection connection = database.connection();
            List<NpcType> result = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new NpcType(rs.getInt("type_id"), rs.getString("type_name"),
                            rs.getInt("group_id"), rs.getString("group_name")));
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to list NPC types", e);
            }
            return result;
        }
    }
}
