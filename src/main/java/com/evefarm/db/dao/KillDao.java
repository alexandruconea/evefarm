package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.db.JdbcUtil;
import com.evefarm.model.KillDayTypeRow;
import com.evefarm.model.KillEvent;
import com.evefarm.model.ParsedKill;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class KillDao {

    private final Database database;

    public KillDao(Database database) {
        this.database = database;
    }

    public void insertIfNew(long characterId, ParsedKill kill) {
        String sql = """
                INSERT INTO character_kills(character_id, killed_at, npc_name, faction_label, solar_system, bounty)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(character_id, killed_at, npc_name) DO UPDATE SET
                  faction_label = excluded.faction_label,
                  solar_system = excluded.solar_system,
                  bounty = excluded.bounty
                """;
        synchronized (database) {
            Connection connection = database.connection();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, characterId);
                ps.setString(2, kill.killedAt().toString());
                ps.setString(3, kill.npcName());
                ps.setString(4, kill.factionLabel());
                ps.setString(5, kill.solarSystem());
                JdbcUtil.setNullable(ps, 6, kill.bounty());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to insert kill for character " + characterId, e);
            }
        }
    }

    public record LogFileProgress(long size, int parserVersion) {
    }

    public java.util.Optional<LogFileProgress> findProgress(String fileName) {
        String sql = "SELECT last_size, parser_version FROM kill_log_progress WHERE file_name = ?";
        synchronized (database) {
            Connection connection = database.connection();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, fileName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return java.util.Optional.empty();
                    }
                    return java.util.Optional.of(
                            new LogFileProgress(rs.getLong("last_size"), rs.getInt("parser_version")));
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to read log progress for " + fileName, e);
            }
        }
    }

    public void saveProgress(String fileName, long size, int parserVersion) {
        String sql = """
                INSERT INTO kill_log_progress(file_name, last_size, parser_version) VALUES (?, ?, ?)
                ON CONFLICT(file_name) DO UPDATE SET
                  last_size = excluded.last_size,
                  parser_version = excluded.parser_version
                """;
        synchronized (database) {
            Connection connection = database.connection();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, fileName);
                ps.setLong(2, size);
                ps.setInt(3, parserVersion);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to save log progress for " + fileName, e);
            }
        }
    }

    public void clearLogProgress() {
        synchronized (database) {
            Connection connection = database.connection();
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM kill_log_progress")) {
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to reset log progress", e);
            }
        }
    }

    public List<String> listDistinctFactions(Set<Long> characterIds) {
        if (characterIds.isEmpty()) {
            return List.of();
        }
        String placeholders = characterIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = "SELECT DISTINCT faction_label FROM character_kills "
                + "WHERE character_id IN (" + placeholders + ") ORDER BY faction_label";
        synchronized (database) {
            Connection connection = database.connection();
            List<String> result = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                int index = 1;
                for (Long id : characterIds) {
                    ps.setLong(index++, id);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(rs.getString("faction_label"));
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to list distinct kill factions", e);
            }
            return result;
        }
    }

    public List<String> listDistinctSystems(Set<Long> characterIds, Set<String> factionLabels) {
        if (characterIds.isEmpty() || factionLabels.isEmpty()) {
            return List.of();
        }
        String characterPlaceholders = characterIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String factionPlaceholders = factionLabels.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = "SELECT DISTINCT COALESCE(solar_system, ?) AS system FROM character_kills "
                + "WHERE character_id IN (" + characterPlaceholders + ") "
                + "AND faction_label IN (" + factionPlaceholders + ") "
                + "ORDER BY system";
        synchronized (database) {
            Connection connection = database.connection();
            List<String> result = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                int index = 1;
                ps.setString(index++, KillDayTypeRow.UNKNOWN_SYSTEM);
                for (Long id : characterIds) {
                    ps.setLong(index++, id);
                }
                for (String label : factionLabels) {
                    ps.setString(index++, label);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(rs.getString("system"));
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to list distinct kill systems", e);
            }
            return result;
        }
    }

    public List<String> listDistinctNpcNames(Set<Long> characterIds, Set<String> factionLabels, String systemFilter) {
        if (characterIds.isEmpty() || factionLabels.isEmpty()) {
            return List.of();
        }
        String characterPlaceholders = characterIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String factionPlaceholders = factionLabels.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = "SELECT DISTINCT npc_name FROM character_kills "
                + "WHERE character_id IN (" + characterPlaceholders + ") "
                + "AND faction_label IN (" + factionPlaceholders + ") "
                + systemFilterClause(systemFilter)
                + "ORDER BY npc_name";
        synchronized (database) {
            Connection connection = database.connection();
            List<String> result = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                int index = 1;
                for (Long id : characterIds) {
                    ps.setLong(index++, id);
                }
                for (String label : factionLabels) {
                    ps.setString(index++, label);
                }
                index = bindSystemFilter(ps, index, systemFilter);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(rs.getString("npc_name"));
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to list distinct kill NPC names", e);
            }
            return result;
        }
    }

    private static String systemFilterClause(String systemFilter) {
        if (systemFilter == null) {
            return "";
        }
        if (KillDayTypeRow.UNKNOWN_SYSTEM.equals(systemFilter)) {
            return "AND solar_system IS NULL ";
        }
        return "AND solar_system = ? ";
    }

    private static int bindSystemFilter(PreparedStatement ps, int index, String systemFilter) throws SQLException {
        if (systemFilter != null && !KillDayTypeRow.UNKNOWN_SYSTEM.equals(systemFilter)) {
            ps.setString(index++, systemFilter);
        }
        return index;
    }

    public List<KillDayTypeRow> listDayTypeRows(Set<Long> characterIds, Set<String> factionLabels,
                                                 Set<String> npcNames, String systemFilter,
                                                 Instant from, Instant to) {
        if (characterIds.isEmpty() || factionLabels.isEmpty() || npcNames.isEmpty()) {
            return List.of();
        }
        String characterPlaceholders = characterIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String factionPlaceholders = factionLabels.stream().map(id -> "?").collect(Collectors.joining(","));
        String namePlaceholders = npcNames.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = "SELECT npc_name, faction_label, solar_system, killed_at FROM character_kills "
                + "WHERE killed_at BETWEEN ? AND ? "
                + "AND character_id IN (" + characterPlaceholders + ") "
                + "AND faction_label IN (" + factionPlaceholders + ") "
                + "AND npc_name IN (" + namePlaceholders + ") "
                + systemFilterClause(systemFilter);
        synchronized (database) {
            Connection connection = database.connection();
            List<KillEvent> events = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                int index = 1;
                ps.setString(index++, from.toString());
                ps.setString(index++, to.toString());
                for (Long id : characterIds) {
                    ps.setLong(index++, id);
                }
                for (String label : factionLabels) {
                    ps.setString(index++, label);
                }
                for (String name : npcNames) {
                    ps.setString(index++, name);
                }
                index = bindSystemFilter(ps, index, systemFilter);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        LocalDate day = Instant.parse(rs.getString("killed_at"))
                                .atZone(ZoneId.systemDefault()).toLocalDate();
                        events.add(new KillEvent(day, rs.getString("faction_label"), rs.getString("npc_name"),
                                rs.getString("solar_system")));
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to list kill day/type rows", e);
            }
            return KillDayTypeRow.groupByDayTypeAndSystem(events);
        }
    }
}
