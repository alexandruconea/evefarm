package com.evefarm.service;

import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.db.dao.AbyssalRunDao;
import com.evefarm.db.dao.ItemTypeDao;
import com.evefarm.model.AbyssFleet;
import com.evefarm.model.AbyssTier;
import com.evefarm.model.AbyssWeather;
import com.evefarm.model.AbyssalLoot;
import com.evefarm.model.ItemType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AbyssLootServiceTest {

    private static final int TRITANIUM = 34;
    private static final int SURVEY_DATABASE = 48121;
    private static final int MUTAPLASMID = 47408;
    private static final int FILAMENT = 47888;

    private AbyssLootService service;
    private AbyssalRunDao runs;

    @BeforeEach
    void setUp() {
        Database database = new Database(":memory:");
        MigrationRunner.run(database);
        ItemTypeDao items = new ItemTypeDao(database);
        items.replaceAll(List.of(
                new ItemType(TRITANIUM, "Tritanium", false),
                new ItemType(SURVEY_DATABASE, "Triglavian Survey Database", false),
                new ItemType(MUTAPLASMID, "Unstable Damage Control Mutaplasmid", false),
                new ItemType(FILAMENT, "Fierce Exotic Filament", false)));
        PriceService prices = mock(PriceService.class);
        when(prices.getUnitPrice(anyInt())).thenReturn(OptionalDouble.empty());
        when(prices.getUnitPrice(TRITANIUM)).thenReturn(OptionalDouble.of(4.0));
        when(prices.getUnitPrice(SURVEY_DATABASE)).thenReturn(OptionalDouble.of(100_000.0));
        when(prices.getUnitPrice(FILAMENT)).thenReturn(OptionalDouble.of(9_500_000.0));
        runs = new AbyssalRunDao(database);
        service = new AbyssLootService(items, prices, runs);
    }

    @Test
    void theGainedItemsArePricedAndTheMostValuableComeFirst() {
        AbyssLootService.LootResult result = service.calculate(
                "Tritanium\t1,000\nFierce Exotic Filament\t2",
                "tritanium\t3,500\nTriglavian Survey Database\t12\nFierce Exotic Filament\t1\n"
                        + "Unstable Damage Control Mutaplasmid\t1\nSome Renamed Container\t1");

        assertEquals(List.of("Triglavian Survey Database", "Tritanium", "Unstable Damage Control Mutaplasmid"),
                result.items().stream().map(AbyssalLoot::typeName).toList());
        assertEquals(2_500L, result.items().get(1).quantity());
        assertNull(result.items().get(2).unitPrice());
        assertEquals(1_210_000.0, result.totalValue());
        assertEquals(List.of("Some Renamed Container"), result.unknownNames());
    }

    @Test
    void ignoredItemsAreLeftOutOfTheLoot() {
        runs.ignoreItem(TRITANIUM, "Tritanium");

        AbyssLootService.LootResult result = service.calculate("Tritanium\t100",
                "Tritanium\t400\nTriglavian Survey Database\t2");

        assertEquals(List.of("Triglavian Survey Database"),
                result.items().stream().map(AbyssalLoot::typeName).toList());
        assertEquals(List.of(new AbyssalLoot(TRITANIUM, "Tritanium", 300, 4.0)), result.ignored());
        assertEquals(200_000.0, result.totalValue());
    }

    @Test
    void aCargoIsRecognisedWhenItNamesAtLeastOneKnownItem() {
        assertEquals(2, service.recognizedItems("Tritanium\t10\nMy Renamed Container\t1"));
        assertEquals(0, service.recognizedItems("Malpais Legate\tBarset"));
        assertEquals(0, service.recognizedItems(""));
    }

    @Test
    void everyShipOfTheFleetUsesAFilamentOfItsOwn() {
        assertEquals(9_500_000.0, service.filamentCost(AbyssTier.FIERCE, AbyssWeather.EXOTIC, AbyssFleet.CRUISER));
        assertEquals(19_000_000.0, service.filamentCost(AbyssTier.FIERCE, AbyssWeather.EXOTIC, AbyssFleet.DESTROYERS));
        assertEquals(28_500_000.0, service.filamentCost(AbyssTier.FIERCE, AbyssWeather.EXOTIC, AbyssFleet.FRIGATES));
        assertEquals(9_500_000.0, service.filamentCost(AbyssTier.FIERCE, AbyssWeather.EXOTIC, null));
        assertNull(service.filamentCost(AbyssTier.CALM, AbyssWeather.DARK, AbyssFleet.FRIGATES));
        assertNull(service.filamentCost(null, AbyssWeather.DARK, AbyssFleet.CRUISER));
    }
}
