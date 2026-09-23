package com.evefarm.service;

import com.evefarm.model.ItemType;
import com.evefarm.model.NpcType;
import com.evefarm.model.SpawnClass;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcCatalogTest {

    private static final NpcCatalog CATALOG = NpcCatalog.of(List.of(
            new NpcType(13536, "Estamel Tharchon", 559, "Asteroid Guristas Officer"),
            new NpcType(34155, "Burner Hawk", 816, "Mission Generic Frigates"),
            new NpcType(16037, "Sansha's Butcher", 555, "Asteroid Sansha's Nation Frigate"),
            new NpcType(99001, "Sansha's Butcher", 700, "Deadspace Sansha's Nation Frigate"),
            new NpcType(99002, "Mercenary Overlord", 821, "Mission Generic Battleships")
    ));

    @Test
    void groupNamesDecideTheSpawnClass() {
        assertEquals(SpawnClass.OFFICER, SpawnClass.fromGroupName("Asteroid Guristas Officer Frigate"));
        assertEquals(SpawnClass.COMMANDER, SpawnClass.fromGroupName("Asteroid Guristas Commander Battleship"));
        assertEquals(SpawnClass.BELT, SpawnClass.fromGroupName("Asteroid Guristas Battleship"));
        assertEquals(SpawnClass.OTHER, SpawnClass.fromGroupName("Deadspace Serpentis Battleship"));
        assertEquals(SpawnClass.OTHER, SpawnClass.fromGroupName("Mission Generic Frigates"));
    }

    @Test
    void aNameSharedByTwoTypesResolvesToTheBeltOneAndKeepsBothIds() {
        NpcCatalog.Entry butcher = CATALOG.find("Sansha's Butcher").orElseThrow();
        assertEquals(SpawnClass.BELT, butcher.spawnClass());
        assertEquals("Asteroid Sansha's Nation Frigate", butcher.groupName());
        assertEquals(Set.of(16037, 99001), Set.copyOf(butcher.typeIds()));
    }

    @Test
    void factionLabelsComeFromTheGroupNotTheName() {
        assertEquals(Optional.of("Guristas Pirates Officers"), CATALOG.factionLabelFor("Estamel Tharchon"),
                "an officer's own name carries no faction word at all");
        assertEquals(Optional.of(NpcCatalog.MISSIONS_LABEL), CATALOG.factionLabelFor("Burner Hawk"));
        assertEquals(Optional.of("Sansha's Nation"), CATALOG.factionLabelFor("Sansha's Butcher"));
        assertEquals(Optional.empty(), CATALOG.factionLabelFor("Slave Pen"), "not in the catalog");
    }

    @Test
    void officerNamesAndTypeIdLookups() {
        assertEquals(Set.of("Estamel Tharchon"), CATALOG.officerNames());
        assertEquals(Optional.of("Burner Hawk"), CATALOG.nameForTypeId(34155));
        assertTrue(CATALOG.nameForTypeId(1).isEmpty());
    }

    @Test
    void itemListKeepsPublishedMarketItemsAndFlagsOfficerModules() {
        String types = "\"typeID\",\"groupID\",\"typeName\",\"published\",\"marketGroupID\"\r\n"
                + "\"1\",\"40\",\"Estamel's Modified Large Shield Booster\",\"1\",\"611\"\r\n"
                + "\"2\",\"40\",\"Large Shield Booster II\",\"1\",\"611\"\r\n"
                + "\"3\",\"40\",\"Unpublished Booster\",\"0\",\"611\"\r\n"
                + "\"4\",\"559\",\"Estamel Tharchon\",\"0\",\"\"\r\n";
        String meta = "\"typeID\",\"parentTypeID\",\"metaGroupID\"\r\n\"1\",\"2\",\"5\"\r\n\"2\",\"\",\"2\"\r\n";

        assertEquals(List.of(new ItemType(1, "Estamel's Modified Large Shield Booster", true),
                        new ItemType(2, "Large Shield Booster II", false)),
                NpcCatalogService.parseItemTypes(types, meta));
    }

    @Test
    void parsesOnlyEntityTypesFromTheSdeExports() {
        String groups = "﻿\"groupID\",\"categoryID\",\"groupName\"\r\n"
                + "\"559\",\"11\",\"Asteroid Guristas Officer\"\r\n"
                + "\"25\",\"6\",\"Frigate\"\r\n";
        String types = "﻿\"typeID\",\"groupID\",\"typeName\",\"description\"\r\n"
                + "\"13536\",\"559\",\"Estamel Tharchon\",\"Line one,\r\nline two \"\"quoted\"\"\"\r\n"
                + "\"587\",\"25\",\"Rifter\",\"A player ship\"\r\n";

        assertEquals(List.of(new NpcType(13536, "Estamel Tharchon", 559, "Asteroid Guristas Officer")),
                NpcCatalogService.parseNpcTypes(groups, types));
    }
}
