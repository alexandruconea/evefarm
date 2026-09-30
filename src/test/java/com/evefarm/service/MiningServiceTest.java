package com.evefarm.service;

import com.evefarm.model.Ore;
import com.evefarm.service.MiningService.Period;
import com.evefarm.service.MiningService.Valuation;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
