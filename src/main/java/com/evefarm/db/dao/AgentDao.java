package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.db.JdbcUtil;
import com.evefarm.model.AgentRow;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class AgentDao {

    private final Database database;

    public AgentDao(Database database) {
        this.database = database;
    }

    public void replaceAll(List<AgentRow> agents) {
        synchronized (database) {
            Connection connection = database.connection();
            String insertSql = """
                    INSERT INTO sde_agent(
                      agent_id, agent_name, corporation_id, corporation_name, faction_id, faction_name,
                      division_name, agent_type_name, level, is_locator, station_id, station_name,
                      solar_system_name, security, constellation_name, region_name)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
            try {
                connection.setAutoCommit(false);
                try (Statement statement = connection.createStatement()) {
                    statement.execute("DELETE FROM sde_agent");
                }
                try (PreparedStatement ps = connection.prepareStatement(insertSql)) {
                    for (AgentRow agent : agents) {
                        ps.setLong(1, agent.agentId());
                        ps.setString(2, agent.agentName());
                        JdbcUtil.setNullable(ps, 3, agent.corporationId());
                        ps.setString(4, agent.corporationName());
                        JdbcUtil.setNullable(ps, 5, agent.factionId());
                        ps.setString(6, agent.factionName());
                        ps.setString(7, agent.divisionName());
                        ps.setString(8, agent.agentTypeName());
                        ps.setInt(9, agent.level());
                        ps.setInt(10, agent.isLocator() ? 1 : 0);
                        JdbcUtil.setNullable(ps, 11, agent.stationId());
                        ps.setString(12, agent.stationName());
                        ps.setString(13, agent.solarSystemName());
                        JdbcUtil.setNullable(ps, 14, agent.security());
                        ps.setString(15, agent.constellationName());
                        ps.setString(16, agent.regionName());
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
                throw new IllegalStateException("Failed to replace the agent catalog", e);
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
            }
        }
    }

    public List<AgentRow> listAll() {
        String sql = """
                SELECT agent_id, agent_name, corporation_id, corporation_name, faction_id, faction_name,
                       division_name, agent_type_name, level, is_locator, station_id, station_name,
                       solar_system_name, security, constellation_name, region_name
                FROM sde_agent
                ORDER BY agent_name
                """;
        synchronized (database) {
            Connection connection = database.connection();
            List<AgentRow> result = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new AgentRow(
                            rs.getLong("agent_id"),
                            rs.getString("agent_name"),
                            JdbcUtil.getNullableLong(rs, "corporation_id"),
                            rs.getString("corporation_name"),
                            JdbcUtil.getNullableLong(rs, "faction_id"),
                            rs.getString("faction_name"),
                            rs.getString("division_name"),
                            rs.getString("agent_type_name"),
                            rs.getInt("level"),
                            rs.getInt("is_locator") != 0,
                            JdbcUtil.getNullableLong(rs, "station_id"),
                            rs.getString("station_name"),
                            rs.getString("solar_system_name"),
                            JdbcUtil.getNullableDouble(rs, "security"),
                            rs.getString("constellation_name"),
                            rs.getString("region_name")
                    ));
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to list agents", e);
            }
            return result;
        }
    }

    public int count() {
        String sql = "SELECT COUNT(*) AS total FROM sde_agent";
        synchronized (database) {
            Connection connection = database.connection();
            try (PreparedStatement ps = connection.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("total") : 0;
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to count agents", e);
            }
        }
    }
}
