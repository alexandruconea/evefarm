package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.model.Accelerator;
import com.evefarm.model.CharacterAccelerator;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class AcceleratorDao {

    private final Database database;

    public AcceleratorDao(Database database) {
        this.database = database;
    }

    public void replaceCatalog(List<Accelerator> accelerators) {
        database.transaction("Failed to save the accelerator list", connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM sde_accelerator");
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO sde_accelerator(type_id, name, bonus, duration_hours) VALUES (?, ?, ?, ?)")) {
                for (Accelerator accelerator : accelerators) {
                    ps.setInt(1, accelerator.typeId());
                    ps.setString(2, accelerator.name());
                    ps.setInt(3, accelerator.bonus());
                    ps.setDouble(4, accelerator.durationHours());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        });
    }

    public List<Accelerator> catalog() {
        synchronized (database) {
            try (Statement statement = database.connection().createStatement();
                 ResultSet rs = statement.executeQuery(
                         "SELECT type_id, name, bonus, duration_hours FROM sde_accelerator ORDER BY type_id")) {
                List<Accelerator> accelerators = new ArrayList<>();
                while (rs.next()) {
                    accelerators.add(new Accelerator(rs.getInt("type_id"), rs.getString("name"), rs.getInt("bonus"),
                            rs.getDouble("duration_hours")));
                }
                return accelerators;
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to read the accelerator list", e);
            }
        }
    }

    public Optional<CharacterAccelerator> find(long characterId) {
        synchronized (database) {
            try (PreparedStatement ps = database.connection().prepareStatement(
                    "SELECT * FROM character_accelerator WHERE character_id = ?")) {
                ps.setLong(1, characterId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    int typeId = rs.getInt("type_id");
                    Integer type = rs.wasNull() ? null : typeId;
                    String endsAt = rs.getString("ends_at");
                    return Optional.of(new CharacterAccelerator(characterId, type, rs.getString("name"),
                            rs.getInt("bonus"), Instant.parse(rs.getString("first_seen")),
                            endsAt == null ? null : Instant.parse(endsAt), rs.getInt("set_by_user") != 0));
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to read the accelerator of character " + characterId, e);
            }
        }
    }

    public void save(CharacterAccelerator accelerator) {
        synchronized (database) {
            try (PreparedStatement ps = database.connection().prepareStatement("""
                    INSERT OR REPLACE INTO character_accelerator(character_id, type_id, name, bonus, first_seen,
                                                                 ends_at, set_by_user)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """)) {
                ps.setLong(1, accelerator.characterId());
                if (accelerator.typeId() == null) {
                    ps.setNull(2, Types.INTEGER);
                } else {
                    ps.setInt(2, accelerator.typeId());
                }
                ps.setString(3, accelerator.name());
                ps.setInt(4, accelerator.bonus());
                ps.setString(5, accelerator.firstSeen().toString());
                ps.setString(6, accelerator.endsAt() == null ? null : accelerator.endsAt().toString());
                ps.setInt(7, accelerator.setByUser() ? 1 : 0);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to save the accelerator of character "
                        + accelerator.characterId(), e);
            }
        }
    }

    public void delete(long characterId) {
        synchronized (database) {
            try (PreparedStatement ps = database.connection().prepareStatement(
                    "DELETE FROM character_accelerator WHERE character_id = ?")) {
                ps.setLong(1, characterId);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to clear the accelerator of character " + characterId, e);
            }
        }
    }
}
