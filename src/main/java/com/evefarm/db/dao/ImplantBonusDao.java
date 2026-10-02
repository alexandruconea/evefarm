package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.model.CharacterAttributes;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

public final class ImplantBonusDao {

    private final Database database;

    public ImplantBonusDao(Database database) {
        this.database = database;
    }

    public Optional<CharacterAttributes> find(int typeId) {
        synchronized (database) {
            try (PreparedStatement ps = database.connection().prepareStatement(
                    "SELECT charisma, intelligence, memory, perception, willpower FROM implant_attribute_bonus "
                            + "WHERE type_id = ?")) {
                ps.setInt(1, typeId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(new CharacterAttributes(rs.getInt("charisma"), rs.getInt("intelligence"),
                            rs.getInt("memory"), rs.getInt("perception"), rs.getInt("willpower")));
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to read the bonus of implant " + typeId, e);
            }
        }
    }

    public void save(int typeId, CharacterAttributes bonus) {
        synchronized (database) {
            try (PreparedStatement ps = database.connection().prepareStatement("""
                    INSERT OR REPLACE INTO implant_attribute_bonus(type_id, charisma, intelligence, memory,
                                                                    perception, willpower, cached_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """)) {
                ps.setInt(1, typeId);
                ps.setInt(2, bonus.charisma());
                ps.setInt(3, bonus.intelligence());
                ps.setInt(4, bonus.memory());
                ps.setInt(5, bonus.perception());
                ps.setInt(6, bonus.willpower());
                ps.setString(7, Instant.now().toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to save the bonus of implant " + typeId, e);
            }
        }
    }
}
