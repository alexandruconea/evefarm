package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.model.JournalPayout;
import com.evefarm.model.OfficerSighting;
import com.evefarm.model.ParsedEncounter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class EncounterDao {

    private final Database database;

    public EncounterDao(Database database) {
        this.database = database;
    }

    public void replaceForLogFile(long characterId, String logFile, List<ParsedEncounter> encounters) {
        String insertEncounter = """
                INSERT INTO combat_encounter(character_id, log_file, started_at, ended_at, solar_system)
                VALUES (?, ?, ?, ?, ?)
                """;
        String insertNpc = """
                INSERT INTO combat_encounter_npc(encounter_id, npc_name, first_seen_at, last_seen_at, kills,
                  bounty, last_kill_at, damage_dealt, damage_taken)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        synchronized (database) {
            Connection connection = database.connection();
            try {
                connection.setAutoCommit(false);
                try (PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM combat_encounter WHERE log_file = ?")) {
                    delete.setString(1, logFile);
                    delete.executeUpdate();
                }
                try (PreparedStatement encounterPs = connection.prepareStatement(insertEncounter,
                        Statement.RETURN_GENERATED_KEYS);
                     PreparedStatement npcPs = connection.prepareStatement(insertNpc)) {
                    for (ParsedEncounter encounter : encounters) {
                        encounterPs.setLong(1, characterId);
                        encounterPs.setString(2, logFile);
                        encounterPs.setString(3, encounter.startedAt().toString());
                        encounterPs.setString(4, encounter.endedAt().toString());
                        encounterPs.setString(5, encounter.solarSystem());
                        encounterPs.executeUpdate();
                        long encounterId;
                        try (ResultSet keys = encounterPs.getGeneratedKeys()) {
                            keys.next();
                            encounterId = keys.getLong(1);
                        }
                        for (ParsedEncounter.Npc npc : encounter.npcs()) {
                            npcPs.setLong(1, encounterId);
                            npcPs.setString(2, npc.name());
                            npcPs.setString(3, npc.firstSeenAt().toString());
                            npcPs.setString(4, npc.lastSeenAt().toString());
                            npcPs.setInt(5, npc.kills());
                            npcPs.setDouble(6, npc.bounty());
                            npcPs.setString(7, npc.lastKillAt() == null ? null : npc.lastKillAt().toString());
                            npcPs.setLong(8, npc.damageDealt());
                            npcPs.setLong(9, npc.damageTaken());
                            npcPs.addBatch();
                        }
                    }
                    npcPs.executeBatch();
                }
                connection.commit();
            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException ignored) {
                }
                throw new IllegalStateException("Failed to store fights from " + logFile, e);
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
            }
        }
    }

    public List<OfficerSighting> listOfficerSightings(Set<String> officerNames) {
        if (officerNames.isEmpty()) {
            return List.of();
        }
        String placeholders = officerNames.stream().map(n -> "?").collect(Collectors.joining(","));
        String sql = """
                SELECT e.id, e.character_id, c.character_name, e.started_at, e.ended_at, e.solar_system,
                       n.npc_name, n.first_seen_at, n.bounty, n.last_kill_at,
                       (SELECT COALESCE(SUM(o.kills), 0) FROM combat_encounter_npc o
                         WHERE o.encounter_id = e.id AND o.npc_name <> n.npc_name) AS escort_kills,
                       (SELECT COUNT(*) FROM combat_encounter_npc o
                         WHERE o.encounter_id = e.id AND o.npc_name <> n.npc_name) AS escort_types,
                       (SELECT COALESCE(SUM(d.quantity * d.unit_price), 0) FROM officer_drop d
                         WHERE d.character_id = e.character_id AND d.officer_name = n.npc_name
                           AND d.first_seen_at = n.first_seen_at) AS drop_value,
                       s.belt, s.notes, s.payout_at, s.payout_amount, s.payout_reason, s.payout_description
                FROM combat_encounter_npc n
                JOIN combat_encounter e ON e.id = n.encounter_id
                JOIN characters c ON c.character_id = e.character_id
                LEFT JOIN officer_sighting s ON s.character_id = e.character_id
                     AND s.officer_name = n.npc_name AND s.first_seen_at = n.first_seen_at
                WHERE n.npc_name IN (%s) AND c.removed_at IS NULL
                ORDER BY n.first_seen_at DESC
                """.formatted(placeholders);
        synchronized (database) {
            Connection connection = database.connection();
            List<OfficerSighting> result = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                int index = 1;
                for (String name : officerNames) {
                    ps.setString(index++, name);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String payoutAt = rs.getString("payout_at");
                        JournalPayout payout = payoutAt == null ? null : new JournalPayout(Instant.parse(payoutAt),
                                rs.getDouble("payout_amount"), rs.getString("payout_reason"),
                                rs.getString("payout_description"));
                        result.add(new OfficerSighting(
                                rs.getLong("id"),
                                rs.getLong("character_id"),
                                rs.getString("character_name"),
                                rs.getString("npc_name"),
                                null,
                                Instant.parse(rs.getString("first_seen_at")),
                                parseInstant(rs.getString("last_kill_at")),
                                rs.getDouble("bounty"),
                                rs.getString("solar_system"),
                                Instant.parse(rs.getString("started_at")),
                                Instant.parse(rs.getString("ended_at")),
                                rs.getInt("escort_kills"),
                                rs.getInt("escort_types"),
                                rs.getDouble("drop_value"),
                                rs.getString("belt"),
                                rs.getString("notes"),
                                payout));
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to list officer sightings", e);
            }
            return result;
        }
    }

    public List<ParsedEncounter.Npc> listEncounterNpcs(long encounterId) {
        String sql = """
                SELECT npc_name, first_seen_at, last_seen_at, kills, bounty, last_kill_at, damage_dealt, damage_taken
                FROM combat_encounter_npc WHERE encounter_id = ? ORDER BY first_seen_at, npc_name
                """;
        synchronized (database) {
            Connection connection = database.connection();
            List<ParsedEncounter.Npc> result = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, encounterId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(new ParsedEncounter.Npc(
                                rs.getString("npc_name"),
                                Instant.parse(rs.getString("first_seen_at")),
                                Instant.parse(rs.getString("last_seen_at")),
                                rs.getInt("kills"),
                                rs.getDouble("bounty"),
                                parseInstant(rs.getString("last_kill_at")),
                                rs.getLong("damage_dealt"),
                                rs.getLong("damage_taken")));
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to list NPCs for fight " + encounterId, e);
            }
            return result;
        }
    }

    private static Instant parseInstant(String value) {
        return value == null ? null : Instant.parse(value);
    }
}
