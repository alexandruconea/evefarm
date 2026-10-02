package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.model.CharacterAttributes;
import com.evefarm.model.CharacterSkills;
import com.evefarm.model.OwnedSkill;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public final class CharacterSkillDao {

    private final Database database;

    public CharacterSkillDao(Database database) {
        this.database = database;
    }

    public void save(CharacterSkills skills) {
        synchronized (database) {
            Connection connection = database.connection();
            try {
                connection.setAutoCommit(false);
                try (PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM character_skill WHERE character_id = ?")) {
                    delete.setLong(1, skills.characterId());
                    delete.executeUpdate();
                }
                try (PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO character_skill(character_id, skill_id, skillpoints, trained_level, active_level)
                        VALUES (?, ?, ?, ?, ?)
                        """)) {
                    for (OwnedSkill skill : skills.skills()) {
                        insert.setLong(1, skills.characterId());
                        insert.setInt(2, skill.skillId());
                        insert.setLong(3, skill.skillPoints());
                        insert.setInt(4, skill.trainedLevel());
                        insert.setInt(5, skill.activeLevel());
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
                try (PreparedStatement attributes = connection.prepareStatement("""
                        INSERT OR REPLACE INTO character_attributes(character_id, charisma, intelligence, memory,
                            perception, willpower, implant_ids, total_sp, unallocated_sp, bonus_remaps,
                            last_remap_date, remap_cooldown_date, fetched_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)) {
                    CharacterAttributes values = skills.attributes();
                    attributes.setLong(1, skills.characterId());
                    attributes.setInt(2, values.charisma());
                    attributes.setInt(3, values.intelligence());
                    attributes.setInt(4, values.memory());
                    attributes.setInt(5, values.perception());
                    attributes.setInt(6, values.willpower());
                    attributes.setString(7, skills.implants().stream().map(String::valueOf)
                            .collect(Collectors.joining(",")));
                    attributes.setLong(8, skills.totalSp());
                    attributes.setLong(9, skills.unallocatedSp());
                    if (skills.bonusRemaps() == null) {
                        attributes.setNull(10, Types.INTEGER);
                    } else {
                        attributes.setInt(10, skills.bonusRemaps());
                    }
                    attributes.setString(11, skills.lastRemapDate());
                    attributes.setString(12, skills.remapCooldownDate());
                    attributes.setString(13, skills.fetchedAt());
                    attributes.executeUpdate();
                }
                connection.commit();
            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException ignored) {
                }
                throw new IllegalStateException("Failed to save the skills of character " + skills.characterId(), e);
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
            }
        }
    }

    public Optional<CharacterSkills> find(long characterId) {
        synchronized (database) {
            Connection connection = database.connection();
            try {
                List<OwnedSkill> owned = new ArrayList<>();
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT skill_id, skillpoints, trained_level, active_level FROM character_skill "
                                + "WHERE character_id = ?")) {
                    ps.setLong(1, characterId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            owned.add(new OwnedSkill(rs.getInt("skill_id"), rs.getLong("skillpoints"),
                                    rs.getInt("trained_level"), rs.getInt("active_level")));
                        }
                    }
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT * FROM character_attributes WHERE character_id = ?")) {
                    ps.setLong(1, characterId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            return Optional.empty();
                        }
                        int bonusRemaps = rs.getInt("bonus_remaps");
                        Integer remaps = rs.wasNull() ? null : bonusRemaps;
                        String implantIds = rs.getString("implant_ids");
                        List<Integer> implants = implantIds == null || implantIds.isBlank() ? List.of()
                                : Arrays.stream(implantIds.split(",")).map(Integer::valueOf).toList();
                        return Optional.of(new CharacterSkills(characterId, List.copyOf(owned),
                                new CharacterAttributes(rs.getInt("charisma"), rs.getInt("intelligence"),
                                        rs.getInt("memory"), rs.getInt("perception"), rs.getInt("willpower")),
                                implants, rs.getLong("total_sp"), rs.getLong("unallocated_sp"), remaps,
                                rs.getString("last_remap_date"), rs.getString("remap_cooldown_date"),
                                rs.getString("fetched_at")));
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to read the skills of character " + characterId, e);
            }
        }
    }
}
