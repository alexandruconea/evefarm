package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.model.ItemType;
import com.evefarm.model.JournalPayout;
import com.evefarm.model.OfficerDrop;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class OfficerDao {

    private final Database database;

    public OfficerDao(Database database) {
        this.database = database;
    }

    public void saveDetails(long characterId, String officerName, Instant firstSeenAt, String belt, String notes) {
        String sql = """
                INSERT INTO officer_sighting(character_id, officer_name, first_seen_at, belt, notes)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(character_id, officer_name, first_seen_at) DO UPDATE SET
                  belt = excluded.belt,
                  notes = excluded.notes
                """;
        synchronized (database) {
            Connection connection = database.connection();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, characterId);
                ps.setString(2, officerName);
                ps.setString(3, firstSeenAt.toString());
                ps.setString(4, belt);
                ps.setString(5, notes);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to save details for " + officerName, e);
            }
        }
    }

    public void savePayout(long characterId, String officerName, Instant firstSeenAt, JournalPayout payout) {
        String sql = """
                INSERT INTO officer_sighting(character_id, officer_name, first_seen_at, payout_at, payout_amount,
                  payout_reason, payout_description)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(character_id, officer_name, first_seen_at) DO UPDATE SET
                  payout_at = excluded.payout_at,
                  payout_amount = excluded.payout_amount,
                  payout_reason = excluded.payout_reason,
                  payout_description = excluded.payout_description
                """;
        synchronized (database) {
            Connection connection = database.connection();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, characterId);
                ps.setString(2, officerName);
                ps.setString(3, firstSeenAt.toString());
                ps.setString(4, payout.paidAt().toString());
                ps.setDouble(5, payout.amount());
                ps.setString(6, payout.reason());
                ps.setString(7, payout.description());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to save the bounty payout for " + officerName, e);
            }
        }
    }

    public void addDrop(long characterId, String officerName, Instant firstSeenAt, ItemType item, int quantity,
                        double unitPrice) {
        String sql = """
                INSERT INTO officer_drop(character_id, officer_name, first_seen_at, type_id, type_name, quantity,
                  unit_price, added_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        synchronized (database) {
            Connection connection = database.connection();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, characterId);
                ps.setString(2, officerName);
                ps.setString(3, firstSeenAt.toString());
                ps.setInt(4, item.typeId());
                ps.setString(5, item.typeName());
                ps.setInt(6, quantity);
                ps.setDouble(7, unitPrice);
                ps.setString(8, Instant.now().toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to add a drop for " + officerName, e);
            }
        }
    }

    public void removeDrop(long dropId) {
        synchronized (database) {
            Connection connection = database.connection();
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM officer_drop WHERE id = ?")) {
                ps.setLong(1, dropId);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to remove drop " + dropId, e);
            }
        }
    }

    public List<OfficerDrop> listDrops(long characterId, String officerName, Instant firstSeenAt) {
        String sql = """
                SELECT id, type_id, type_name, quantity, unit_price, added_at FROM officer_drop
                WHERE character_id = ? AND officer_name = ? AND first_seen_at = ?
                ORDER BY quantity * unit_price DESC, type_name
                """;
        synchronized (database) {
            Connection connection = database.connection();
            List<OfficerDrop> result = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, characterId);
                ps.setString(2, officerName);
                ps.setString(3, firstSeenAt.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(new OfficerDrop(rs.getLong("id"), rs.getInt("type_id"), rs.getString("type_name"),
                                rs.getInt("quantity"), rs.getDouble("unit_price"),
                                Instant.parse(rs.getString("added_at"))));
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to list drops for " + officerName, e);
            }
            return result;
        }
    }

    public List<String> listBelts(String solarSystem) {
        String sql = "SELECT belt_name FROM asteroid_belt_cache WHERE solar_system = ? ORDER BY belt_name";
        synchronized (database) {
            Connection connection = database.connection();
            List<String> result = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, solarSystem);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(rs.getString("belt_name"));
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to list belts for " + solarSystem, e);
            }
            return result;
        }
    }

    public void saveBelts(String solarSystem, Map<Long, String> beltNamesById) {
        String sql = """
                INSERT INTO asteroid_belt_cache(solar_system, belt_id, belt_name) VALUES (?, ?, ?)
                ON CONFLICT(solar_system, belt_id) DO UPDATE SET belt_name = excluded.belt_name
                """;
        synchronized (database) {
            Connection connection = database.connection();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                for (Map.Entry<Long, String> belt : beltNamesById.entrySet()) {
                    ps.setString(1, solarSystem);
                    ps.setLong(2, belt.getKey());
                    ps.setString(3, belt.getValue());
                    ps.addBatch();
                }
                ps.executeBatch();
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to save belts for " + solarSystem, e);
            }
        }
    }
}
