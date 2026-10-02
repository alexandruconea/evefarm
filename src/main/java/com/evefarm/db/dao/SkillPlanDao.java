package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.model.SkillPlan;
import com.evefarm.model.SkillPlanEntry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class SkillPlanDao {

    private final Database database;

    public SkillPlanDao(Database database) {
        this.database = database;
    }

    public List<SkillPlan> listPlans(long characterId) {
        synchronized (database) {
            try (PreparedStatement ps = database.connection().prepareStatement(
                    "SELECT plan_id, name FROM skill_plan WHERE character_id = ? ORDER BY name COLLATE NOCASE")) {
                ps.setLong(1, characterId);
                List<SkillPlan> plans = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        plans.add(new SkillPlan(rs.getLong("plan_id"), characterId, rs.getString("name")));
                    }
                }
                return plans;
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to list the skill plans", e);
            }
        }
    }

    public SkillPlan create(long characterId, String name) {
        synchronized (database) {
            try (PreparedStatement ps = database.connection().prepareStatement(
                    "INSERT INTO skill_plan(character_id, name, created_at) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, characterId);
                ps.setString(2, name);
                ps.setString(3, Instant.now().toString());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    return new SkillPlan(keys.getLong(1), characterId, name);
                }
            } catch (SQLException e) {
                throw nameError(name, e);
            }
        }
    }

    public void rename(long planId, String name) {
        synchronized (database) {
            try (PreparedStatement ps = database.connection().prepareStatement(
                    "UPDATE skill_plan SET name = ? WHERE plan_id = ?")) {
                ps.setString(1, name);
                ps.setLong(2, planId);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw nameError(name, e);
            }
        }
    }

    public void delete(long planId) {
        synchronized (database) {
            Connection connection = database.connection();
            try {
                connection.setAutoCommit(false);
                for (String sql : List.of("DELETE FROM skill_plan_entry WHERE plan_id = ?",
                        "DELETE FROM skill_plan WHERE plan_id = ?")) {
                    try (PreparedStatement ps = connection.prepareStatement(sql)) {
                        ps.setLong(1, planId);
                        ps.executeUpdate();
                    }
                }
                connection.commit();
            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException ignored) {
                }
                throw new IllegalStateException("Failed to delete skill plan " + planId, e);
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
            }
        }
    }

    public List<SkillPlanEntry> entries(long planId) {
        synchronized (database) {
            try (PreparedStatement ps = database.connection().prepareStatement(
                    "SELECT skill_id, level, planned, notes FROM skill_plan_entry WHERE plan_id = ? "
                            + "ORDER BY position")) {
                ps.setLong(1, planId);
                List<SkillPlanEntry> entries = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        entries.add(new SkillPlanEntry(rs.getInt("skill_id"), rs.getInt("level"),
                                rs.getInt("planned") != 0, rs.getString("notes")));
                    }
                }
                return entries;
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to read skill plan " + planId, e);
            }
        }
    }

    public void replaceEntries(long planId, List<SkillPlanEntry> entries) {
        synchronized (database) {
            Connection connection = database.connection();
            try {
                connection.setAutoCommit(false);
                try (PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM skill_plan_entry WHERE plan_id = ?")) {
                    delete.setLong(1, planId);
                    delete.executeUpdate();
                }
                try (PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO skill_plan_entry(plan_id, position, skill_id, level, planned, notes)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """)) {
                    for (int i = 0; i < entries.size(); i++) {
                        SkillPlanEntry entry = entries.get(i);
                        insert.setLong(1, planId);
                        insert.setInt(2, i);
                        insert.setInt(3, entry.skillId());
                        insert.setInt(4, entry.level());
                        insert.setInt(5, entry.planned() ? 1 : 0);
                        insert.setString(6, entry.notes());
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
                connection.commit();
            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException ignored) {
                }
                throw new IllegalStateException("Failed to save skill plan " + planId, e);
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
            }
        }
    }

    private static IllegalStateException nameError(String name, SQLException e) {
        if (e.getMessage() != null && e.getMessage().contains("UNIQUE")) {
            return new IllegalStateException("A plan named '" + name + "' already exists for this character", e);
        }
        return new IllegalStateException("Failed to save the skill plan '" + name + "'", e);
    }
}
