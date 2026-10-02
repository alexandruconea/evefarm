package com.evefarm.service;

import com.evefarm.model.StandingRow;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StandingServiceTest {

    private static final StandingRow CALDARI = row(1, "faction", "Caldari State", 3.0, null, null);
    private static final StandingRow GALLENTE = row(1, "faction", "Gallente Federation", 5.0, null, null);
    private static final StandingRow NAVY = row(1, "npc_corp", "Caldari Navy", 4.1, null, "Caldari State");
    private static final StandingRow LAI_DAI = row(1, "npc_corp", "Lai Dai Corporation", 6.2, null, "Caldari State");
    private static final StandingRow SISTERS = row(1, "npc_corp", "Sisters of EVE", 3.3, null,
            "Servant Sisters of EVE");
    private static final StandingRow UNLISTED = row(1, "npc_corp", "Unlisted Corp", 9.0, null, null);
    private static final StandingRow NAVY_AGENT = row(1, "agent", "Antaken Kamola", 2.5, "Caldari Navy",
            "Caldari State");
    private static final StandingRow LAI_DAI_AGENT = row(1, "agent", "Aikoka Taikkala", 1.0, "Lai Dai Corporation",
            "Caldari State");
    private static final StandingRow SISTERS_AGENT = row(1, "agent", "Sister Alitura", 8.2, "Sisters of EVE",
            "Servant Sisters of EVE");
    private static final StandingRow OTHER_PILOTS_AGENT = row(2, "agent", "Antaken Kamola", 1.5, "Caldari Navy",
            "Caldari State");
    private static final List<StandingRow> ROWS = List.of(CALDARI, GALLENTE, NAVY, LAI_DAI, SISTERS, UNLISTED,
            NAVY_AGENT, LAI_DAI_AGENT, SISTERS_AGENT, OTHER_PILOTS_AGENT);

    private static StandingRow row(long characterId, String fromType, String name, double standing,
                                   String corporationName, String factionName) {
        return new StandingRow(characterId, "Pilot " + characterId, name.hashCode(), fromType, name, standing,
                corporationName, factionName, null, null, null);
    }

    @Test
    void eachFactionIsFollowedByItsCorporationsBestFirst() {
        assertEquals(List.of(GALLENTE, CALDARI, LAI_DAI, NAVY, SISTERS, UNLISTED),
                StandingService.factionsAndCorporations(ROWS));
    }

    @Test
    void onlyCorporationsListedUnderTheirFactionAreIndented() {
        assertEquals(Set.of(NAVY, LAI_DAI), StandingService.corporationsUnderTheirFaction(ROWS));
    }

    @Test
    void aFactionOrCorporationShowsItsOwnAgentsForTheSameCharacter() {
        assertEquals(List.of(NAVY_AGENT, LAI_DAI_AGENT), StandingService.agents(ROWS, CALDARI));
        assertEquals(List.of(NAVY_AGENT), StandingService.agents(ROWS, NAVY));
        assertEquals(List.of(SISTERS_AGENT), StandingService.agents(ROWS, SISTERS));
        assertEquals(List.of(NAVY_AGENT, LAI_DAI_AGENT, SISTERS_AGENT, OTHER_PILOTS_AGENT),
                StandingService.agents(ROWS, null));
    }

    @Test
    void eveTypesGetPlainNames() {
        assertEquals("Faction", StandingService.kind("faction"));
        assertEquals("Corporation", StandingService.kind("npc_corp"));
        assertEquals("Agent", StandingService.kind("agent"));
        assertEquals("", StandingService.kind(null));
    }
}
