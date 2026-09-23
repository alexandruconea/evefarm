package com.evefarm.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LpOfferPricingServiceTest {

    private final LpOfferPricingService service = new LpOfferPricingService(null, null, null, null);

    @Test
    void computeProfitSubtractsIskCostAndOtherCostFromOutputValue() {
        assertEquals(930.0, service.computeProfit(100.0, 10, 50.0, 20.0));
    }

    @Test
    void computeProfitIsNullWhenOutputPriceIsUnknown() {
        assertNull(service.computeProfit(null, 10, 50.0, 20.0));
    }

    @Test
    void computeProfitIsNullWhenOtherCostIsUnknown() {
        assertNull(service.computeProfit(100.0, 10, 50.0, null));
    }

    @Test
    void computeIskPerLpDividesProfitByLpCost() {
        assertEquals(9.3, service.computeIskPerLp(100.0, 10, 50.0, 100, 20.0));
    }

    @Test
    void computeIskPerLpIsNullWhenLpCostIsZeroOrNegative() {
        assertNull(service.computeIskPerLp(100.0, 10, 50.0, 0, 20.0));
        assertNull(service.computeIskPerLp(100.0, 10, 50.0, -5, 20.0));
    }

    @Test
    void computeIskPerLpIsNullWhenProfitIsUnknown() {
        assertNull(service.computeIskPerLp(null, 10, 50.0, 100, 20.0));
    }

    @Test
    void materialEfficiencyReducesQuantityByThePercentAndRoundsUp() {
        assertEquals(95, service.applyMaterialEfficiency(100, 1, 5));
        assertEquals(29, service.applyMaterialEfficiency(3, 10, 5));
    }

    @Test
    void materialEfficiencyNeverGoesBelowOnePerRun() {
        assertEquals(1, service.applyMaterialEfficiency(1, 1, 5));
        assertEquals(4, service.applyMaterialEfficiency(1, 4, 5));
    }

    @Test
    void zeroMaterialEfficiencyAppliesNoReduction() {
        assertEquals(200, service.applyMaterialEfficiency(50, 4, 0));
    }

    @Test
    void requiredItemCostIsUnknownWithoutUnboxingWhenAnyPriceIsMissing() {
        assertNull(LpOfferPricingService.requiredItemsCost(false, false, 123.0));
    }

    @Test
    void requiredItemCostIsZeroWhenTheOfferRequiresNoItems() {
        assertEquals(0.0, LpOfferPricingService.requiredItemsCost(true, false, 123.0));
    }
}
