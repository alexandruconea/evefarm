package com.evefarm.service;

import com.evefarm.db.dao.AgentDao;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.esi.FuzzworkApi;
import com.evefarm.model.AgentRow;
import com.evefarm.util.CsvTable;

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

    record RawCorporation(String name, Integer factionId) {
    }

    record RawStation(String name, int solarSystemId) {
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
                    regionName,
                    solarSystem == null ? null : (long) solarSystem.solarSystemId(),
                    solarSystem == null ? null : (long) solarSystem.constellationId(),
                    solarSystem == null ? null : (long) solarSystem.regionId()
            ));
        }
        return rows;
    }

    private static Map<Integer, String> parseIdNameMap(String csvText, String idColumn, String nameColumn) {
        CsvTable table = CsvTable.parse(csvText);
        Map<Integer, String> result = new HashMap<>();
        for (List<String> row : table.rows()) {
            Integer id = table.integer(row, idColumn);
            String name = table.text(row, nameColumn);
            if (id != null && name != null) {
                result.put(id, name);
            }
        }
        return result;
    }

    private static List<RawAgent> parseAgents(String csvText) {
        CsvTable table = CsvTable.parse(csvText);
        List<RawAgent> result = new ArrayList<>();
        for (List<String> row : table.rows()) {
            Long agentId = table.number(row, "agentID");
            Integer divisionId = table.integer(row, "divisionID");
            Integer corporationId = table.integer(row, "corporationID");
            Long stationId = table.number(row, "locationID");
            Integer level = table.integer(row, "level");
            Integer agentTypeId = table.integer(row, "agentTypeID");
            if (agentId == null || divisionId == null || corporationId == null || stationId == null
                    || level == null || agentTypeId == null) {
                continue;
            }
            result.add(new RawAgent(agentId, divisionId, corporationId, stationId, level, agentTypeId,
                    "1".equals(table.text(row, "isLocator"))));
        }
        return result;
    }

    private static Map<Integer, RawCorporation> parseCorporations(String csvText) {
        CsvTable table = CsvTable.parse(csvText);
        Map<Integer, RawCorporation> result = new HashMap<>();
        for (List<String> row : table.rows()) {
            Integer corporationId = table.integer(row, "corporationID");
            String name = table.text(row, "corporationName");
            if (corporationId != null && name != null) {
                result.put(corporationId, new RawCorporation(name, table.integer(row, "factionID")));
            }
        }
        return result;
    }

    private static Map<Long, RawStation> parseStations(String csvText) {
        CsvTable table = CsvTable.parse(csvText);
        Map<Long, RawStation> result = new HashMap<>();
        for (List<String> row : table.rows()) {
            Long stationId = table.number(row, "stationID");
            String name = table.text(row, "stationName");
            Integer solarSystemId = table.integer(row, "solarSystemID");
            if (stationId != null && name != null && solarSystemId != null) {
                result.put(stationId, new RawStation(name, solarSystemId));
            }
        }
        return result;
    }

    private static Map<Integer, RawSolarSystem> parseSolarSystems(String csvText) {
        CsvTable table = CsvTable.parse(csvText);
        Map<Integer, RawSolarSystem> result = new HashMap<>();
        for (List<String> row : table.rows()) {
            Integer solarSystemId = table.integer(row, "solarSystemID");
            String name = table.text(row, "solarSystemName");
            Integer constellationId = table.integer(row, "constellationID");
            Integer regionId = table.integer(row, "regionID");
            if (solarSystemId != null && name != null && constellationId != null && regionId != null) {
                result.put(solarSystemId, new RawSolarSystem(solarSystemId, name, table.decimal(row, "security"),
                        constellationId, regionId));
            }
        }
        return result;
    }

    private static Map<Long, String> parseAgentNames(String csvText, Set<Long> agentIds) {
        CsvTable table = CsvTable.parse(csvText);
        Map<Long, String> result = new HashMap<>();
        for (List<String> row : table.rows()) {
            Long itemId = table.number(row, "itemID");
            if (itemId == null || !agentIds.contains(itemId)) {
                continue;
            }
            String name = table.text(row, "itemName");
            if (name != null) {
                result.put(itemId, name);
            }
        }
        return result;
    }
}
