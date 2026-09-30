package com.evefarm.service;

import com.evefarm.service.AbyssSpawnCatalog.Ewar;
import com.evefarm.service.AbyssSpawnCatalog.Faction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AbyssSpawnCatalogTest {

    private static Faction factionOf(String name) {
        return AbyssSpawnCatalog.identify(name).orElseThrow().faction();
    }

    @Test
    void everyAbyssalFactionIsRecognisedByName() {
        assertEquals(Faction.TRIGLAVIANS, factionOf("Striking Damavik"));
        assertEquals(Faction.TRIGLAVIANS, factionOf("Anchoring Vila Damavik"));
        assertEquals(Faction.TRIGLAVIANS, factionOf("Vila Swarmer"));
        assertEquals(Faction.ROGUE_DRONES, factionOf("Sparkneedle Tessella"));
        assertEquals(Faction.ROGUE_DRONES, factionOf("Embergrip Tessera"));
        assertEquals(Faction.ROGUE_DRONES, factionOf("Bathyic Abyssal Overmind"));
        assertEquals(Faction.DRIFTERS, factionOf("Karybdis Tyrannos"));
        assertEquals(Faction.DRIFTERS, factionOf("Ephialtes Lancer"));
        assertEquals(Faction.DRIFTERS, factionOf("Drifter Nullwarp Cruiser"));
        assertEquals(Faction.SLEEPERS, factionOf("Lucid Deepwatcher"));
        assertEquals(Faction.ANGELS, factionOf("Elite Lucifer Cynabal"));
        assertEquals(Faction.SANSHA, factionOf("Devoted Knight"));
        assertEquals(Faction.EDENCOM, factionOf("Drainer Marshal Disparu Troop"));
    }

    @Test
    void pilotsCachesAndOtherNpcsAreNotPartOfASpawn() {
        for (String name : List.of("Malpais Legate", "Nozeu", "Triglavian Biocombinative Cache",
                "Triglavian Extraction Node", "Unstable Abyssal Depths", "Imperial Navy Sentinel", "")) {
            assertEquals(Optional.empty(), AbyssSpawnCatalog.identify(name), name);
        }
    }

    @Test
    void eachShipBringsItsEwar() {
        assertEquals(Set.of(Ewar.NEUT), AbyssSpawnCatalog.identify("Starving Damavik").orElseThrow().ewar());
        assertEquals(Set.of(Ewar.SCRAM, Ewar.WEB), AbyssSpawnCatalog.identify("Lucifer Echo").orElseThrow().ewar());
        assertEquals(Set.of(Ewar.WEB), AbyssSpawnCatalog.identify("Elite Lucifer Dramiel").orElseThrow().ewar());
        assertEquals(Set.of(Ewar.REPAIR), AbyssSpawnCatalog.identify("Renewing Rodiva").orElseThrow().ewar());
        assertEquals(Set.of(), AbyssSpawnCatalog.identify("Lucid Escort").orElseThrow().ewar());
    }

    @Test
    void aDrifterRoomSaysHowToFightTheKarybdis() {
        String speech = AbyssSpawnCatalog.report(1,
                List.of("Scylla Tyrannos", "Karybdis Tyrannos", "Ephialtes Lancer")).speech();

        assertEquals("Room 1: Drifters, with Karybdis and Lancer. Karybdis: spiral in to under 30 kilometers.",
                speech);
    }

    @Test
    void aMixedRoomListsItsFactionsAndTheEwarToExpect() {
        String speech = AbyssSpawnCatalog.report(3, List.of("Sparkneedle Tessella", "Strikeneedle Tessella",
                "Anchoring Damavik", "Harrowing Vedmak", "Tangling Damavik", "Striking Damavik")).speech();

        assertEquals("Room 3: Rogue drones and Triglavians, with Vedmak. Watch for scrams and webs.", speech);
    }

    @Test
    void theCheatSheetTipsAreGivenForTheRoomsThatNeedThem() {
        assertEquals(List.of("Tessera hit hard up close: stay over 12 kilometers away."),
                AbyssSpawnCatalog.report(1, List.of("Embergrip Tessera", "Sparkgrip Tessera")).advice());
        assertEquals(List.of("Kill the Vila ships to stop their drones; killed drones come back."),
                AbyssSpawnCatalog.report(1, List.of("Vila Swarmer", "Ghosting Vila Damavik")).advice());
        assertEquals(List.of("Kite the Sansha: they are slow but very dangerous up close."),
                AbyssSpawnCatalog.report(1, List.of("Devoted Knight", "Devoted Smith")).advice());
        assertEquals("Room 2: EDENCOM, with Marshal. Keep your speed up until the room is under control. "
                        + "Kill the Marshal first, it has little health. Watch for neuts.",
                AbyssSpawnCatalog.report(2, List.of("Drainer Marshal Disparu Troop", "Stormbringer Disparu Troop"))
                        .speech());
    }
}
