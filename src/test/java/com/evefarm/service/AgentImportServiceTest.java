package com.evefarm.service;

import com.evefarm.model.AgentRow;
import com.evefarm.service.AgentImportService.RawAgent;
import com.evefarm.service.AgentImportService.RawCorporation;
import com.evefarm.service.AgentImportService.RawSolarSystem;
import com.evefarm.service.AgentImportService.RawStation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentImportServiceTest {

    @Test
    void joinsAllReferenceDataIntoAFlatRow() {
        List<RawAgent> agents = List.of(new RawAgent(3008416L, 22, 1000002, 60000004L, 1, 2, false));
        Map<Integer, String> agentTypes = Map.of(2, "BasicAgent");
        Map<Integer, String> divisions = Map.of(22, "Distribution");
        Map<Integer, RawCorporation> corporations = Map.of(1000002, new RawCorporation(1000002, "CBD Corporation", 500001));
        Map<Integer, String> factions = Map.of(500001, "Caldari State");
        Map<Long, RawStation> stations = Map.of(60000004L, new RawStation(60000004L, "Muvolailen 10 - Moon 3", 30002780));
        Map<Integer, RawSolarSystem> solarSystems = Map.of(30002780, new RawSolarSystem(30002780, "Muvolailen", 0.7, 20000407, 10000033));
        Map<Integer, String> constellations = Map.of(20000407, "IX-DOA");
        Map<Integer, String> regions = Map.of(10000033, "The Forge");
        Map<Long, String> agentNames = Map.of(3008416L, "Kaarira Vaerta");

        List<AgentRow> rows = AgentImportService.buildAgentRows(agents, agentTypes, divisions, corporations,
                factions, stations, solarSystems, constellations, regions, agentNames);

        assertEquals(1, rows.size());
        AgentRow row = rows.get(0);
        assertEquals(3008416L, row.agentId());
        assertEquals("Kaarira Vaerta", row.agentName());
        assertEquals("CBD Corporation", row.corporationName());
        assertEquals("Caldari State", row.factionName());
        assertEquals("Distribution", row.divisionName());
        assertEquals("BasicAgent", row.agentTypeName());
        assertEquals("Muvolailen 10 - Moon 3", row.stationName());
        assertEquals("Muvolailen", row.solarSystemName());
        assertEquals(0.7, row.security());
        assertEquals("IX-DOA", row.constellationName());
        assertEquals("The Forge", row.regionName());
        assertEquals(1, row.level());
        assertTrue(!row.isLocator());
    }

    @Test
    void anAgentWithNoResolvableNameIsSkippedEntirely() {
        List<RawAgent> agents = List.of(new RawAgent(999L, 22, 1000002, 60000004L, 1, 2, false));

        List<AgentRow> rows = AgentImportService.buildAgentRows(agents, Map.of(), Map.of(), Map.of(),
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());

        assertEquals(0, rows.size());
    }

    @Test
    void aCorporationWithNoFactionLeavesFactionFieldsNullWithoutFailing() {
        List<RawAgent> agents = List.of(new RawAgent(3008416L, 22, 1000002, 60000004L, 1, 2, false));
        Map<Integer, RawCorporation> corporations = Map.of(1000002, new RawCorporation(1000002, "InterBus", null));
        Map<Long, String> agentNames = Map.of(3008416L, "Kaarira Vaerta");

        List<AgentRow> rows = AgentImportService.buildAgentRows(agents, Map.of(), Map.of(), corporations,
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), agentNames);

        assertEquals(1, rows.size());
        assertEquals("InterBus", rows.get(0).corporationName());
        assertNull(rows.get(0).factionId());
        assertNull(rows.get(0).factionName());
    }

    @Test
    void anUnresolvableStationLeavesLocationFieldsNullWithoutFailing() {
        List<RawAgent> agents = List.of(new RawAgent(3008416L, 22, 1000002, 60000004L, 1, 2, false));
        Map<Long, String> agentNames = Map.of(3008416L, "Kaarira Vaerta");

        List<AgentRow> rows = AgentImportService.buildAgentRows(agents, Map.of(), Map.of(), Map.of(),
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), agentNames);

        assertEquals(1, rows.size());
        AgentRow row = rows.get(0);
        assertEquals(60000004L, row.stationId());
        assertNull(row.stationName());
        assertNull(row.solarSystemName());
        assertNull(row.security());
    }
}
