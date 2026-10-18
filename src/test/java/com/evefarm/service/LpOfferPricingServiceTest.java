package com.evefarm.service;

import com.evefarm.esi.FuzzworkApi;
import com.evefarm.esi.LoyaltyApi;
import com.evefarm.esi.dto.FuzzworkBlueprintDto;
import com.evefarm.esi.dto.LoyaltyOfferDto;
import com.evefarm.model.IndustryActivity;
import com.evefarm.model.LpOfferRow;
import com.evefarm.model.PriceMode;
import com.evefarm.model.TypeInfo;
import com.evefarm.model.TypeQuantity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LpOfferPricingServiceTest {

    private static final long CALDARI_NAVY = 1000035L;
    private static final int WEBIFIER_BLUEPRINT = 17560;
    private static final int WEBIFIER = 17559;
    private static final int TRITANIUM = 34;
    private static final int PYERITE = 35;

    private final LpOfferPricingService service = new LpOfferPricingService(null, null, null, null, null, null);

    @Test
    void blueprintOffersArePricedWithFreshPricesOfWhatTheyBuildAndUse() {
        LoyaltyApi loyalty = mock(LoyaltyApi.class);
        FuzzworkApi fuzzwork = mock(FuzzworkApi.class);
        TypeNameCacheService types = mock(TypeNameCacheService.class);
        PriceService prices = mock(PriceService.class);
        MarketHistoryService history = mock(MarketHistoryService.class);
        when(loyalty.listStoreOffers(CALDARI_NAVY)).thenReturn(List.of(
                new LoyaltyOfferDto(1, WEBIFIER_BLUEPRINT, 1, 10_000, 1_000_000, 0, List.of()),
                new LoyaltyOfferDto(2, WEBIFIER_BLUEPRINT, 5, 50_000, 5_000_000, 0, List.of())));
        when(types.resolveType(WEBIFIER_BLUEPRINT)).thenReturn(new TypeInfo(WEBIFIER_BLUEPRINT,
                "Caldari Navy Stasis Webifier Blueprint", "Stasis Web Blueprint", "Blueprint", 0.01));
        when(fuzzwork.fetchBlueprintMaterials(WEBIFIER_BLUEPRINT)).thenReturn(Optional.of(new FuzzworkBlueprintDto(
                new FuzzworkBlueprintDto.BlueprintDetails(WEBIFIER, 1),
                Map.of(FuzzworkBlueprintDto.ACTIVITY_MANUFACTURING, List.of(
                        new FuzzworkBlueprintDto.MaterialEntry(TRITANIUM, "Tritanium", 1_000),
                        new FuzzworkBlueprintDto.MaterialEntry(PYERITE, "Pyerite", 200))))));
        when(prices.getSellVolumes()).thenReturn(Map.of());
        when(prices.getUnitPrice(anyInt(), any(PriceMode.class))).thenReturn(OptionalDouble.empty());
        when(prices.getUnitPrice(eq(WEBIFIER), any(PriceMode.class))).thenReturn(OptionalDouble.of(3_000_000));
        when(prices.getUnitPrice(TRITANIUM, PriceMode.SELL_MIN)).thenReturn(OptionalDouble.of(4));
        when(prices.getUnitPrice(PYERITE, PriceMode.SELL_MIN)).thenReturn(OptionalDouble.of(10));
        when(history.dailyVolumes(any())).thenReturn(Map.of(WEBIFIER, 12.0));

        List<LpOfferRow> rows = new LpOfferPricingService(loyalty, fuzzwork, mock(IndustryCatalogService.class),
                types, prices, history).listPricedOffers(CALDARI_NAVY);

        verify(prices).ensureFreshPrices(Set.of(WEBIFIER_BLUEPRINT, WEBIFIER, TRITANIUM, PYERITE));
        verify(types).resolveTypes(Set.of(WEBIFIER));
        verify(fuzzwork, times(1)).fetchBlueprintMaterials(WEBIFIER_BLUEPRINT);
        LpOfferRow one = rows.getFirst();
        assertEquals(WEBIFIER, one.outputTypeId());
        assertEquals(950 * 4.0 + 190 * 10.0, one.buildMaterialsCost(), 1e-9);
        assertEquals((3_000_000 - 1_000_000 - 5_700) / 10_000.0, one.iskPerLpSell(), 1e-9);
        LpOfferRow five = rows.get(1);
        assertEquals(5, five.outputUnits());
        assertEquals(4_750 * 4.0 + 950 * 10.0, five.buildMaterialsCost(), 1e-9);
        assertEquals(12.0, five.dailyVolume(), 1e-9);
    }

    @Test
    void blueprintOffersUseTheBlueprintDataOfTheIndustryTabWhenItHasIt() {
        LoyaltyApi loyalty = mock(LoyaltyApi.class);
        FuzzworkApi fuzzwork = mock(FuzzworkApi.class);
        IndustryCatalogService catalog = mock(IndustryCatalogService.class);
        TypeNameCacheService types = mock(TypeNameCacheService.class);
        PriceService prices = mock(PriceService.class);
        MarketHistoryService history = mock(MarketHistoryService.class);
        when(loyalty.listStoreOffers(CALDARI_NAVY)).thenReturn(List.of(
                new LoyaltyOfferDto(1, WEBIFIER_BLUEPRINT, 1, 10_000, 1_000_000, 0, List.of())));
        when(types.resolveType(WEBIFIER_BLUEPRINT)).thenReturn(new TypeInfo(WEBIFIER_BLUEPRINT,
                "Caldari Navy Stasis Webifier Blueprint", "Stasis Web Blueprint", "Blueprint", 0.01));
        when(catalog.activity(WEBIFIER_BLUEPRINT, IndustryActivity.MANUFACTURING)).thenReturn(Optional.of(
                new IndustryActivity(WEBIFIER_BLUEPRINT, IndustryActivity.MANUFACTURING, 600,
                        List.of(new TypeQuantity(TRITANIUM, 1_000), new TypeQuantity(PYERITE, 200)),
                        List.of(new TypeQuantity(WEBIFIER, 1)), Map.of(), List.of())));
        when(catalog.names(List.of(TRITANIUM, PYERITE))).thenReturn(Map.of(TRITANIUM, "Tritanium", PYERITE, "Pyerite"));
        when(prices.getSellVolumes()).thenReturn(Map.of());
        when(prices.getUnitPrice(anyInt(), any(PriceMode.class))).thenReturn(OptionalDouble.empty());
        when(prices.getUnitPrice(eq(WEBIFIER), any(PriceMode.class))).thenReturn(OptionalDouble.of(3_000_000));
        when(prices.getUnitPrice(TRITANIUM, PriceMode.SELL_MIN)).thenReturn(OptionalDouble.of(4));
        when(prices.getUnitPrice(PYERITE, PriceMode.SELL_MIN)).thenReturn(OptionalDouble.of(10));
        when(history.dailyVolumes(any())).thenReturn(Map.of());

        LpOfferRow row = new LpOfferPricingService(loyalty, fuzzwork, catalog, types, prices, history)
                .listPricedOffers(CALDARI_NAVY).getFirst();

        verify(fuzzwork, never()).fetchBlueprintMaterials(anyInt());
        assertEquals(WEBIFIER, row.outputTypeId());
        assertEquals("950x Tritanium, 190x Pyerite", row.buildMaterialsSummary());
        assertEquals(950 * 4.0 + 190 * 10.0, row.buildMaterialsCost(), 1e-9);
    }

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
