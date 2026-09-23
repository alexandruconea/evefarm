package com.evefarm.service;

import com.evefarm.db.dao.AgentDao;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.esi.FuzzworkApi;
import com.evefarm.model.AgentRow;
import com.evefarm.util.CsvParsing;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AgentImportService {

    private final FuzzworkApi fuzzworkApi;
    private final AgentDao agentDao;
    private final SettingsDao settingsDao;

    public AgentImportService(FuzzworkApi fuzzworkApi, AgentDao agentDao, SettingsDao settingsDao) {
        this.fuzzworkApi = fuzzworkApi;
        this.agentDao = agentDao;
        this.settingsDao = settingsDao;
    }

    record RawAgent(long agentId, int divisionId, int corporationId, long stationId, int level,
                     int agentTypeId, boolean isLocator) {
    }

    record RawCorporation(int corporationId, String name, Integer factionId) {
    }

    record RawStation(long stationId, String name, int solarSystemId) {
    }

    record RawSolarSystem(int solarSystemId, String name, Double security, int constellationId, int regionId) {
    }

    public int importAgents() {
        List<RawAgent> rawAgents = parseAgents(fuzzworkApi.fetchStaticDataCsv("agtAgents.csv"));
        Map<Integer, String> agentTypes = parseIdNameMap(
                fuzzworkApi.fetchStaticDataCsv("agtAgentTypes.csv"), "agentTypeID", "agentType");
        Map<Integer, String> divisions = parseIdNameMap(
                fuzzworkApi.fetchStaticDataCsv("crpNPCDivisions.csv"), "divisionID", "divisionName");
        Map<Integer, RawCorporation> corporations = parseCorporations(
                fuzzworkApi.fetchStaticDataCsv("crpNPCCorporations.csv"));
        Map<Integer, String> factions = parseIdNameMap(
                fuzzworkApi.fetchStaticDataCsv("chrFactions.csv"), "factionID", "factionName");
        Map<Long, RawStation> stations = parseStations(fuzzworkApi.fetchStaticDataCsv("staStations.csv"));
        Map<Integer, RawSolarSystem> solarSystems = parseSolarSystems(
                fuzzworkApi.fetchStaticDataCsv("mapSolarSystems.csv"));
        Map<Integer, String> constellations = parseIdNameMap(
                fuzzworkApi.fetchStaticDataCsv("mapConstellations.csv"), "constellationID", "constellationName");
        Map<Integer, String> regions = parseIdNameMap(
                fuzzworkApi.fetchStaticDataCsv("mapRegions.csv"), "regionID", "regionName");

        Set<Long> agentIds = new HashSet<>();
        for (RawAgent agent : rawAgents) {
            agentIds.add(agent.agentId());
        }
        Map<Long, String> agentNames = parseAgentNames(fuzzworkApi.fetchStaticDataCsv("invUniqueNames.csv"), agentIds);

        List<AgentRow> rows = buildAgentRows(rawAgents, agentTypes, divisions, corporations, factions, stations,
                solarSystems, constellations, regions, agentNames);

        agentDao.replaceAll(rows);
        settingsDao.set(SettingsDao.AGENTS_LAST_IMPORTED_AT, Instant.now().toString());
        return rows.size();
    }

    static List<AgentRow> buildAgentRows(List<RawAgent> rawAgents, Map<Integer, String> agentTypes,
            Map<Integer, String> divisions, Map<Integer, RawCorporation> corporations,
            Map<Integer, String> factions, Map<Long, RawStation> stations,
            Map<Integer, RawSolarSystem> solarSystems, Map<Integer, String> constellations,
            Map<Integer, String> regions, Map<Long, String> agentNames) {
        List<AgentRow> rows = new ArrayList<>();
        for (RawAgent agent : rawAgents) {
            String agentName = agentNames.get(agent.agentId());
            if (agentName == null) {
                continue;
            }

            RawCorporation corporation = corporations.get(agent.corporationId());
            String corporationName = corporation == null ? null : corporation.name();
            Integer factionId = corporation == null ? null : corporation.factionId();
            String factionName = factionId == null ? null : factions.get(factionId);

            RawStation station = stations.get(agent.stationId());
            String stationName = station == null ? null : station.name();
            RawSolarSystem solarSystem = station == null ? null : solarSystems.get(station.solarSystemId());
            String solarSystemName = solarSystem == null ? null : solarSystem.name();
            Double security = solarSystem == null ? null : solarSystem.security();
            String constellationName = solarSystem == null ? null : constellations.get(solarSystem.constellationId());
            String regionName = solarSystem == null ? null : regions.get(solarSystem.regionId());

            rows.add(new AgentRow(
                    agent.agentId(),
                    agentName,
                    (long) agent.corporationId(),
                    corporationName,
                    factionId == null ? null : (long) factionId,
                    factionName,
                    divisions.get(agent.divisionId()),
                    agentTypes.get(agent.agentTypeId()),
                    agent.level(),
                    agent.isLocator(),
                    agent.stationId(),
                    stationName,
                    solarSystemName,
                    security,
                    constellationName,
                    regionName
            ));
        }
        return rows;
    }

    private record CsvTable(Map<String, Integer> columnIndex, List<List<String>> rows) {
        String get(List<String> row, String column) {
            Integer index = columnIndex.get(column);
            return (index == null || index >= row.size()) ? null : row.get(index);
        }
    }

    private static CsvTable parseCsv(String csvText) {
        String text = csvText.startsWith("﻿") ? csvText.substring(1) : csvText;
        String[] lines = text.split("\r?\n");
        if (lines.length == 0) {
            return new CsvTable(Map.of(), List.of());
        }
        List<String> header = CsvParsing.parseLine(lines[0]);
        Map<String, Integer> columnIndex = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            columnIndex.put(header.get(i), i);
        }
        List<List<String>> rows = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isBlank()) {
                continue;
            }
            rows.add(CsvParsing.parseLine(lines[i]));
        }
        return new CsvTable(columnIndex, rows);
    }

    private static Map<Integer, String> parseIdNameMap(String csvText, String idColumn, String nameColumn) {
        CsvTable table = parseCsv(csvText);
        Map<Integer, String> result = new HashMap<>();
        for (List<String> row : table.rows()) {
            Integer id = parseIntOrNull(table.get(row, idColumn));
            String name = table.get(row, nameColumn);
            if (id != null && name != null) {
                result.put(id, name);
            }
        }
        return result;
    }

    private static List<RawAgent> parseAgents(String csvText) {
        CsvTable table = parseCsv(csvText);
        List<RawAgent> result = new ArrayList<>();
        for (List<String> row : table.rows()) {
            Long agentId = parseLongOrNull(table.get(row, "agentID"));
            Integer divisionId = parseIntOrNull(table.get(row, "divisionID"));
            Integer corporationId = parseIntOrNull(table.get(row, "corporationID"));
            Long stationId = parseLongOrNull(table.get(row, "locationID"));
            Integer level = parseIntOrNull(table.get(row, "level"));
            Integer agentTypeId = parseIntOrNull(table.get(row, "agentTypeID"));
            if (agentId == null || divisionId == null || corporationId == null || stationId == null
                    || level == null || agentTypeId == null) {
                continue;
            }
            result.add(new RawAgent(agentId, divisionId, corporationId, stationId, level, agentTypeId,
                    "1".equals(table.get(row, "isLocator"))));
        }
        return result;
    }

    private static Map<Integer, RawCorporation> parseCorporations(String csvText) {
        CsvTable table = parseCsv(csvText);
        Map<Integer, RawCorporation> result = new HashMap<>();
        for (List<String> row : table.rows()) {
            Integer corporationId = parseIntOrNull(table.get(row, "corporationID"));
            String name = table.get(row, "corporationName");
            if (corporationId != null && name != null) {
                result.put(corporationId, new RawCorporation(corporationId, name, parseIntOrNull(table.get(row, "factionID"))));
            }
        }
        return result;
    }

    private static Map<Long, RawStation> parseStations(String csvText) {
        CsvTable table = parseCsv(csvText);
        Map<Long, RawStation> result = new HashMap<>();
        for (List<String> row : table.rows()) {
            Long stationId = parseLongOrNull(table.get(row, "stationID"));
            String name = table.get(row, "stationName");
            Integer solarSystemId = parseIntOrNull(table.get(row, "solarSystemID"));
            if (stationId != null && name != null && solarSystemId != null) {
                result.put(stationId, new RawStation(stationId, name, solarSystemId));
            }
        }
        return result;
    }

    private static Map<Integer, RawSolarSystem> parseSolarSystems(String csvText) {
        CsvTable table = parseCsv(csvText);
        Map<Integer, RawSolarSystem> result = new HashMap<>();
        for (List<String> row : table.rows()) {
            Integer solarSystemId = parseIntOrNull(table.get(row, "solarSystemID"));
            String name = table.get(row, "solarSystemName");
            Integer constellationId = parseIntOrNull(table.get(row, "constellationID"));
            Integer regionId = parseIntOrNull(table.get(row, "regionID"));
            if (solarSystemId != null && name != null && constellationId != null && regionId != null) {
                result.put(solarSystemId, new RawSolarSystem(solarSystemId, name,
                        parseDoubleOrNull(table.get(row, "security")), constellationId, regionId));
            }
        }
        return result;
    }

    private static Map<Long, String> parseAgentNames(String csvText, Set<Long> agentIds) {
        CsvTable table = parseCsv(csvText);
        Map<Long, String> result = new HashMap<>();
        for (List<String> row : table.rows()) {
            Long itemId = parseLongOrNull(table.get(row, "itemID"));
            if (itemId == null || !agentIds.contains(itemId)) {
                continue;
            }
            String name = table.get(row, "itemName");
            if (name != null) {
                result.put(itemId, name);
            }
        }
        return result;
    }

    private static Integer parseIntOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long parseLongOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double parseDoubleOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
