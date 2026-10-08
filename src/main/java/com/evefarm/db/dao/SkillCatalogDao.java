package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.model.SkillInfo;
import com.evefarm.model.SkillRequirement;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SkillCatalogDao {

    private final Database database;

    public SkillCatalogDao(Database database) {
        this.database = database;
    }

    public void replaceAll(List<SkillInfo> skills) {
        database.transaction("Failed to save the skill catalog", connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM sde_skill_requirement");
                statement.executeUpdate("DELETE FROM sde_skill");
            }
            try (PreparedStatement skill = connection.prepareStatement("""
                    INSERT INTO sde_skill(type_id, name, group_name, description, skill_rank,
                                          primary_attribute, secondary_attribute)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """);
                 PreparedStatement requirement = connection.prepareStatement("""
                         INSERT OR REPLACE INTO sde_skill_requirement(type_id, required_type_id, required_level)
                         VALUES (?, ?, ?)
                         """)) {
                for (SkillInfo info : skills) {
                    skill.setInt(1, info.skillId());
                    skill.setString(2, info.name());
                    skill.setString(3, info.groupName());
                    skill.setString(4, info.description());
                    skill.setInt(5, info.rank());
                    skill.setString(6, info.primaryAttribute());
                    skill.setString(7, info.secondaryAttribute());
                    skill.addBatch();
                    for (SkillRequirement required : info.requirements()) {
                        requirement.setInt(1, info.skillId());
                        requirement.setInt(2, required.skillId());
                        requirement.setInt(3, required.level());
                        requirement.addBatch();
                    }
                }
                skill.executeBatch();
                requirement.executeBatch();
            }
        });
    }

    public Map<Integer, SkillInfo> loadAll() {
        synchronized (database) {
            Connection connection = database.connection();
            try (Statement statement = connection.createStatement()) {
                Map<Integer, List<SkillRequirement>> requirements = new HashMap<>();
                try (ResultSet rs = statement.executeQuery(
                        "SELECT type_id, required_type_id, required_level FROM sde_skill_requirement "
                                + "ORDER BY type_id, rowid")) {
                    while (rs.next()) {
                        requirements.computeIfAbsent(rs.getInt("type_id"), id -> new ArrayList<>())
                                .add(new SkillRequirement(rs.getInt("required_type_id"), rs.getInt("required_level")));
                    }
                }
                Map<Integer, SkillInfo> skills = new LinkedHashMap<>();
                try (ResultSet rs = statement.executeQuery("""
                        SELECT type_id, name, group_name, description, skill_rank, primary_attribute,
                               secondary_attribute
                        FROM sde_skill ORDER BY group_name, name
                        """)) {
                    while (rs.next()) {
                        int id = rs.getInt("type_id");
                        skills.put(id, new SkillInfo(id, rs.getString("name"), rs.getString("group_name"),
                                rs.getString("description"), rs.getInt("skill_rank"),
                                rs.getString("primary_attribute"), rs.getString("secondary_attribute"),
                                List.copyOf(requirements.getOrDefault(id, List.of()))));
                    }
                }
                return skills;
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to load the skill catalog", e);
            }
        }
    }
}
