package com.evefarm.service;

import com.evefarm.model.Ore;
import com.evefarm.model.RefinedMaterial;
import com.evefarm.model.TypeInfo;
import com.evefarm.service.MiningService.Period;
import com.evefarm.service.MiningService.Valuation;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiningServiceTest {

    private static final int VELDSPAR = 1230;
    private static final int COMPRESSED_VELDSPAR = 28432;
    private static final int TRITANIUM = 34;
    private static final int PYERITE = 35;
    private static final Ore ORE = new Ore(VELDSPAR, 100, COMPRESSED_VELDSPAR, Map.of(TRITANIUM, 400L, PYERITE, 10L));
    private static final Map<Integer, Double> PRICES = Map.of(
            VELDSPAR, 9.99,
            COMPRESSED_VELDSPAR, 11.29,
            TRITANIUM, 4.0,
            PYERITE, 10.0);

    @Test
    void eachValuationPricesAUnitOfOreItsOwnWay() {
        assertEquals(9.99, MiningService.unitValue(Valuation.ORE, VELDSPAR, ORE, PRICES, 0.9));
        assertEquals(11.29, MiningService.unitValue(Valuation.COMPRESSED, VELDSPAR, ORE, PRICES, 0.9),
                "one unit of ore compresses into one unit of compressed ore");
        assertEquals((400 * 4.0 + 10 * 10.0) * 0.9 / 100, MiningService.unitValue(Valuation.REFINED, VELDSPAR, ORE,
                PRICES, 0.9), 1e-9);
    }

    @Test
    void theOrePriceIsUsedWhenTheOtherValuesAreUnknown() {
        Ore bare = new Ore(VELDSPAR, 100, null, Map.of());
        assertEquals(9.99, MiningService.unitValue(Valuation.COMPRESSED, VELDSPAR, bare, PRICES, 0.9));
        assertEquals(9.99, MiningService.unitValue(Valuation.REFINED, VELDSPAR, bare, PRICES, 0.9));
        assertEquals(9.99, MiningService.unitValue(Valuation.REFINED, VELDSPAR, null, PRICES, 0.9));
        assertNull(MiningService.unitValue(Valuation.ORE, 999, null, PRICES, 0.9));
    }

    @Test
    void theCompressedOreIsShownOnlyWhenItsPriceIsUsed() {
        assertEquals(COMPRESSED_VELDSPAR, MiningService.compressedType(Valuation.COMPRESSED, ORE, PRICES));
        assertNull(MiningService.compressedType(Valuation.ORE, ORE, PRICES));
        assertNull(MiningService.compressedType(Valuation.REFINED, ORE, PRICES));
        assertNull(MiningService.compressedType(Valuation.COMPRESSED, ORE, Map.of(VELDSPAR, 9.99)),
                "valued as mined, so shown as mined");
        assertNull(MiningService.compressedType(Valuation.COMPRESSED, null, PRICES));
    }

    @Test
    void theMineralsOfARowAddUpToItsRefinedValue() {
        Map<Integer, TypeInfo> types = Map.of(
                TRITANIUM, new TypeInfo(TRITANIUM, "Tritanium", "Mineral", "Material", 0.01),
                PYERITE, new TypeInfo(PYERITE, "Pyerite", "Mineral", "Material", 0.01));
        List<RefinedMaterial> materials = MiningService.refinedMaterials(1_000, ORE, PRICES, 0.9, types::get);

        assertEquals(List.of("Tritanium", "Pyerite"), materials.stream().map(RefinedMaterial::name).toList());
        assertEquals(3_600.0, materials.getFirst().quantity(), 1e-9, "10 portions of 400, at 90%");
        assertEquals(36.0, materials.getFirst().volume(), 1e-9);
        assertEquals(14_400.0, materials.getFirst().value(), 1e-9);
        assertEquals(90.0, materials.get(1).quantity(), 1e-9);
        double total = materials.stream().mapToDouble(RefinedMaterial::value).sum();
        assertEquals(MiningService.unitValue(Valuation.REFINED, VELDSPAR, ORE, PRICES, 0.9) * 1_000, total, 1e-6);
    }

    @Test
    void mineralsWithoutAPriceAreStillListed() {
        Map<Integer, Double> prices = Map.of(TRITANIUM, 4.0);
        assertTrue(MiningService.refines(Valuation.REFINED, ORE, prices));
        assertFalse(MiningService.refines(Valuation.REFINED, ORE, Map.of(VELDSPAR, 9.99)));
        assertFalse(MiningService.refines(Valuation.ORE, ORE, PRICES));

        List<RefinedMaterial> materials = MiningService.refinedMaterials(100, ORE, prices, 1.0,
                typeId -> new TypeInfo(typeId, "Type #" + typeId, null, null, 0));
        assertEquals(2, materials.size());
        assertNull(materials.get(1).value());
    }

    @Test
    void theCompressedFormAndMineralsArePricedToo() {
        assertEquals(Set.of(VELDSPAR, COMPRESSED_VELDSPAR, TRITANIUM, PYERITE),
                MiningService.pricedTypes(Set.of(VELDSPAR), Map.of(VELDSPAR, ORE)));
    }

    @Test
    void oresAreSortedIntoKinds() {
        assertEquals("Ore", MiningService.kind("Veldspar"));
        assertEquals("Moon ore", MiningService.kind("Rare Moon Asteroids"));
        assertEquals("Ice", MiningService.kind("Ice"));
        assertEquals("Gas", MiningService.kind("Harvestable Cloud"));
        assertEquals("Ore", MiningService.kind(null));
    }

    @Test
    void periodsStartOnTheRightDay() {
        LocalDate today = LocalDate.of(2026, 9, 30);
        assertEquals("2026-09-30", Period.TODAY.fromDate(today));
        assertEquals("2026-09-24", Period.WEEK.fromDate(today));
        assertEquals("2026-09-01", Period.MONTH.fromDate(today));
        assertEquals("2026-09-01", Period.THIS_MONTH.fromDate(today));
        assertNull(Period.ALL.fromDate(today));
    }
}
