package com.evefarm.service;

import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.db.dao.IndustryCatalogDao;
import com.evefarm.esi.dto.TypeDetailsDto;
import com.evefarm.model.BlueprintChoice;
import com.evefarm.model.Decryptor;
import com.evefarm.model.IndustryActivity;
import com.evefarm.model.IndustryCatalog;
import com.evefarm.model.IndustryType;
import com.evefarm.model.SkillRequirement;
import com.evefarm.model.SolarSystem;
import com.evefarm.model.TypeQuantity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndustryCatalogServiceTest {

    private static final String ACTIVITIES = "﻿\"typeID\",\"activityID\",\"time\"\n"
            + "\"691\",\"1\",\"6000\"\n\"691\",\"8\",\"63900\"\n\"11400\",\"1\",\"120000\"\n";
    private static final String MATERIALS = "\"typeID\",\"activityID\",\"materialTypeID\",\"quantity\"\n"
            + "\"691\",\"1\",\"34\",\"32000\"\n\"691\",\"8\",\"20171\",\"2\"\n\"11400\",\"1\",\"587\",\"1\"\n";
    private static final String PRODUCTS = "\"typeID\",\"activityID\",\"productTypeID\",\"quantity\"\n"
            + "\"691\",\"1\",\"587\",\"1\"\n\"691\",\"8\",\"11400\",\"10\"\n\"11400\",\"1\",\"11377\",\"1\"\n";
    private static final String PROBABILITIES = "\"typeID\",\"activityID\",\"productTypeID\",\"probability\"\n"
            + "\"691\",\"8\",\"11400\",\"0.30\"\n";
    private static final String SKILLS = "\"typeID\",\"activityID\",\"skillID\",\"level\"\n"
            + "\"691\",\"8\",\"11442\",\"1\"\n";
    private static final String TYPES = "\"typeID\",\"groupID\",\"typeName\",\"published\"\n"
            + "\"34\",\"18\",\"Tritanium\",\"1\"\n\"587\",\"25\",\"Rifter\",\"1\"\n"
            + "\"691\",\"105\",\"Rifter Blueprint\",\"1\"\n"
            + "\"11377\",\"324\",\"Jaguar\",\"1\"\n\"11400\",\"105\",\"Jaguar Blueprint\",\"1\"\n"
            + "\"20171\",\"333\",\"Datacore - Mechanical Engineering\",\"1\"\n\"99999\",\"25\",\"Unused\",\"1\"\n";
    private static final String GROUPS = "\"groupID\",\"categoryID\",\"groupName\"\n\"18\",\"4\",\"Mineral\"\n"
            + "\"25\",\"6\",\"Frigate\"\n\"105\",\"9\",\"Frigate Blueprint\"\n\"324\",\"6\",\"Assault Frigate\"\n"
            + "\"333\",\"17\",\"Datacores\"\n";
    private static final String CATEGORIES = "\"categoryID\",\"categoryName\"\n\"4\",\"Material\"\n\"6\",\"Ship\"\n"
            + "\"9\",\"Blueprint\"\n\"17\",\"Commodity\"\n";
    private static final String SYSTEMS = "﻿\"regionID\",\"solarSystemID\",\"solarSystemName\",\"security\"\n"
            + "\"10000002\",\"30000142\",\"Jita\",\"0.945913\"\n"
            + "\"10000002\",\"30000140\",\"Maurasi\",\"0.902\"\n"
            + "\"10000060\",\"30004759\",\"1DQ1-A\",\"-0.385782\"\n"
            + "\"11000001\",\"31000005\",\"Thera\",\"-0.99\"\n"
            + "\"12000001\",\"32000001\",\"AD001\",\"-1.0\"\n";
    private static final String REGIONS = "\"regionID\",\"regionName\"\n\"10000002\",\"The Forge\"\n"
            + "\"10000060\",\"Delve\"\n\"11000001\",\"G-R00031\"\n\"12000001\",\"ADR01\"\n";

    private static IndustryCatalog catalog() {
        return IndustryCatalogService.parse(ACTIVITIES, MATERIALS, PRODUCTS, PROBABILITIES, SKILLS, TYPES, GROUPS,
                CATEGORIES);
    }

    private static SolarSystem system(String name) {
        return new SolarSystem(name.hashCode(), name, 0.5, "Region");
    }

    @Test
    void theStaticDataIsReadIntoActivities() {
        IndustryCatalog catalog = catalog();

        IndustryActivity invention = catalog.activities().stream()
                .filter(activity -> activity.blueprintId() == 691 && activity.activityId() == 8)
                .findFirst().orElseThrow();
        assertEquals(63_900, invention.timeSeconds());
        assertEquals(List.of(new TypeQuantity(20171, 2)), invention.materials());
        assertEquals(List.of(new TypeQuantity(11400, 10)), invention.products());
        assertEquals(0.30, invention.probability(11400), 1e-12);
        assertEquals(List.of(new SkillRequirement(11442, 1)), invention.skills());
        assertEquals(3, catalog.activities().size());
    }

    @Test
    void onlyTypesTheBlueprintsUseAreKept() {
        Map<Integer, IndustryType> types = catalog().types().stream()
                .collect(Collectors.toMap(IndustryType::typeId, type -> type));

        assertFalse(types.containsKey(99999));
        assertEquals(new IndustryType(11377, "Jaguar", "Assault Frigate", "Ship", true), types.get(11377));
        assertEquals("Blueprint", types.get(691).categoryName());
    }

    @Test
    void theCatalogIsSavedAndReadBack() {
        Database database = new Database(":memory:");
        MigrationRunner.run(database);
        IndustryCatalogDao dao = new IndustryCatalogDao(database);
        Decryptor accelerant = new Decryptor(34201, "Accelerant Decryptor", 1.2, 2, 10, 1);

        List<SolarSystem> systems = IndustryCatalogService.parseSolarSystems(SYSTEMS, REGIONS);

        dao.replaceAll(catalog(), List.of(accelerant), systems);

        assertEquals(systems, dao.solarSystems());
        assertEquals(4, dao.countSolarSystems());
        assertEquals(List.of("Jaguar", "Rifter"), dao.manufacturingChoices().stream()
                .map(BlueprintChoice::productName).toList());
        assertEquals(Optional.of(691), dao.inventedFrom(11400));
        assertEquals(691, dao.producedBy(587).orElseThrow().blueprintId());
        assertEquals(IndustryActivity.MANUFACTURING, dao.producedBy(587).orElseThrow().activityId());
        assertEquals(Optional.empty(), dao.producedBy(34));
        assertEquals(Optional.empty(), dao.inventedFrom(691));
        IndustryActivity invention = dao.activity(691, IndustryActivity.INVENTION).orElseThrow();
        assertEquals(0.30, invention.probability(11400), 1e-12);
        assertEquals(List.of(new TypeQuantity(11400, 10)), invention.products());
        assertEquals(List.of(accelerant), dao.decryptors());
        assertEquals(Map.of(587, "Rifter"), dao.names(Set.of(587)));
        assertTrue(dao.countActivities() > 0);
    }

    @Test
    void blueprintsThatOnlyTurnAnItemIntoItselfAreNotOffered() {
        IndustryCatalog catalog = IndustryCatalogService.parse(ACTIVITIES + "\"2748\",\"1\",\"300\"\n",
                MATERIALS + "\"2748\",\"1\",\"14343\",\"1\"\n", PRODUCTS + "\"2748\",\"1\",\"14343\",\"1\"\n",
                PROBABILITIES, SKILLS,
                TYPES + "\"2748\",\"105\",\"Silo Blueprint\",\"1\"\n\"14343\",\"25\",\"Silo\",\"1\"\n", GROUPS,
                CATEGORIES);
        Database database = new Database(":memory:");
        MigrationRunner.run(database);
        IndustryCatalogDao dao = new IndustryCatalogDao(database);

        dao.replaceAll(catalog, List.of(), List.of());

        assertEquals(List.of("Jaguar", "Rifter"), dao.manufacturingChoices().stream()
                .map(BlueprintChoice::productName).toList());
    }

    @Test
    void solarSystemsKeepTheirRegionAndSkipAbyssalSpace() {
        List<SolarSystem> systems = IndustryCatalogService.parseSolarSystems(SYSTEMS, REGIONS);

        assertEquals(List.of("1DQ1-A", "Jita", "Maurasi", "Thera"), systems.stream().map(SolarSystem::name).toList());
        assertEquals(new SolarSystem(30000142, "Jita", 0.945913, "The Forge"), systems.get(1));
        assertEquals("Delve", systems.getFirst().regionName());
    }

    @Test
    void suggestionsPutShortNamesThatStartWithTheTextFirst() {
        List<SolarSystem> systems = List.of(system("5V-BJI"), system("JI-1UQ"), system("Jinizu"), system("Jita"),
                system("Maurasi"));

        assertEquals(List.of("Jita", "JI-1UQ", "Jinizu", "5V-BJI"),
                IndustryCatalogService.matchingSystems(systems, " ji ", 10).stream().map(SolarSystem::name).toList());
        assertEquals(List.of("Jita", "JI-1UQ"),
                IndustryCatalogService.matchingSystems(systems, "JI", 2).stream().map(SolarSystem::name).toList());
        assertEquals(List.of(), IndustryCatalogService.matchingSystems(systems, "  ", 10));
        assertEquals(List.of(), IndustryCatalogService.matchingSystems(systems, "Amarr", 10));
    }

    @Test
    void aDecryptorIsReadFromItsDogmaAttributes() {
        TypeDetailsDto type = new TypeDetailsDto(34201, "Accelerant Decryptor", "", true, List.of(
                new TypeDetailsDto.DogmaAttribute(1112, 1.2), new TypeDetailsDto.DogmaAttribute(1113, 2),
                new TypeDetailsDto.DogmaAttribute(1114, 10), new TypeDetailsDto.DogmaAttribute(1124, 1)));

        assertEquals(Optional.of(new Decryptor(34201, "Accelerant Decryptor", 1.2, 2, 10, 1)),
                IndustryCatalogService.toDecryptor(type));
    }
}
