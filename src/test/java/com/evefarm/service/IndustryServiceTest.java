package com.evefarm.service;

import com.evefarm.model.Decryptor;
import com.evefarm.model.IndustryActivity;
import com.evefarm.model.SkillRequirement;
import com.evefarm.model.SolarSystem;
import com.evefarm.model.TypeQuantity;
import com.evefarm.service.IndustryCalculator.Rig;
import com.evefarm.service.IndustryCalculator.Structure;
import com.evefarm.service.IndustryService.Option;
import com.evefarm.service.IndustryService.Result;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IndustryServiceTest {

    private static final SolarSystem JITA = new SolarSystem(30000142L, "Jita", 0.9459, "The Forge");
    private static final int RIFTER_BLUEPRINT = 691;
    private static final int RIFTER = 587;
    private static final int JAGUAR_BLUEPRINT = 11401;
    private static final int JAGUAR = 11400;
    private static final int TRITANIUM = 34;
    private static final int PYERITE = 35;
    private static final int DATACORE = 20410;
    private static final int ENCRYPTION_METHODS = 21790;
    private static final int MECHANICAL_ENGINEERING = 3394;
    private static final Decryptor ACCELERANT = new Decryptor(34201, "Accelerant Decryptor", 1.2, 2, 10, 1);
    private static final Decryptor ATTAINMENT = new Decryptor(34202, "Attainment Decryptor", 1.8, -1, 4, 4);

    private final IndustryCatalogService catalog = mock(IndustryCatalogService.class);
    private final IndustryMarketService market = mock(IndustryMarketService.class);
    private final PriceService prices = mock(PriceService.class);
    private final IndustryService industry = new IndustryService(catalog, market, prices, mock(SkillService.class),
            mock(AssetService.class));

    private static IndustryService.Settings settings(int blueprintId, boolean invention) {
        return new IndustryService.Settings(blueprintId, null, 10, 10, 20, invention, "Jita", Structure.NPC_STATION,
                Rig.NONE, Rig.NONE, 0, 0.02, BuildPlanner.Mode.BUY, 0, 0, Map.of(), BuildPlanner.Surplus.KEEP,
                IndustryService.Stock.NONE);
    }

    private static IndustryActivity manufacturing(int blueprintId, int productId, TypeQuantity... materials) {
        return new IndustryActivity(blueprintId, IndustryActivity.MANUFACTURING, 6_000, Arrays.asList(materials),
                List.of(new TypeQuantity(productId, 1)), Map.of(), List.of());
    }

    @Test
    void aJobPaysForItsMaterialsAndFeeAndSellsAfterTheSalesTaxAndBrokerFee() {
        when(catalog.activity(RIFTER_BLUEPRINT, IndustryActivity.MANUFACTURING)).thenReturn(Optional.of(
                manufacturing(RIFTER_BLUEPRINT, RIFTER, new TypeQuantity(TRITANIUM, 32_000),
                        new TypeQuantity(PYERITE, 6_000))));
        when(catalog.findSystem("Jita")).thenReturn(Optional.of(JITA));
        when(catalog.names(any())).thenReturn(Map.of(RIFTER, "Rifter"));
        when(market.adjustedPrices()).thenReturn(Map.of(TRITANIUM, 3.0, PYERITE, 10.0));
        when(market.costIndex(JITA.systemId(), "manufacturing")).thenReturn(0.05);
        when(prices.getUnitPrices()).thenReturn(Map.of(TRITANIUM, 4.0, PYERITE, 12.0, RIFTER, 500_000.0));

        Result result = industry.calculate(settings(RIFTER_BLUEPRINT, false));

        assertEquals("Rifter", result.productName());
        assertTrue(result.skills().assumed(), "without a character every skill counts as level IV");
        assertFalse(result.reactionsAllowed(), "no reactions in high-sec");
        assertEquals(1, result.options().size());
        Option option = result.options().getFirst();
        assertNull(option.decryptor());
        assertEquals(288_000 * 4.0 + 54_000 * 12.0, option.plan().materialsCost(), 1e-6,
                "ME 10 takes 10% off 10 runs of materials");
        assertEquals((32_000 * 10 * 3.0 + 6_000 * 10 * 10.0) * (0.05 + 0.0025 + 0.04),
                option.manufacturing().jobFee(), 1e-6, "the fee is on the adjusted prices, at the cost index of Jita");
        assertEquals(5_000_000 * (1 - 0.075 * (1 - 0.44) - 0.02), option.sale().net(), 1e-6);
        assertEquals(option.sale().net() - option.totalCost(), option.profit(), 1e-6);
        assertEquals(Duration.ofSeconds(Math.round(10 * 6_000 * 0.8 * 0.84 * 0.88)), option.manufacturing().time(),
                "TE 20, Industry IV and Advanced Industry IV");
    }

    @Test
    void inventionComparesEveryDecryptorAndPicksTheMostProfitable() {
        IndustryActivity invention = new IndustryActivity(RIFTER_BLUEPRINT, IndustryActivity.INVENTION, 60_000,
                List.of(new TypeQuantity(DATACORE, 8)), List.of(new TypeQuantity(JAGUAR_BLUEPRINT, 10)),
                Map.of(JAGUAR_BLUEPRINT, 0.34), List.of(new SkillRequirement(MECHANICAL_ENGINEERING, 1),
                new SkillRequirement(ENCRYPTION_METHODS, 1)));
        when(catalog.activity(JAGUAR_BLUEPRINT, IndustryActivity.MANUFACTURING)).thenReturn(Optional.of(
                manufacturing(JAGUAR_BLUEPRINT, JAGUAR, new TypeQuantity(TRITANIUM, 50_000))));
        when(catalog.inventedFrom(JAGUAR_BLUEPRINT)).thenReturn(Optional.of(RIFTER_BLUEPRINT));
        when(catalog.activity(RIFTER_BLUEPRINT, IndustryActivity.INVENTION)).thenReturn(Optional.of(invention));
        when(catalog.decryptors()).thenReturn(List.of(ACCELERANT, ATTAINMENT));
        when(catalog.findSystem("Jita")).thenReturn(Optional.of(JITA));
        when(catalog.names(any())).thenReturn(Map.of(JAGUAR, "Jaguar"));
        when(market.adjustedPrices()).thenReturn(Map.of());
        when(prices.getUnitPrices()).thenReturn(Map.of(DATACORE, 100_000.0, JAGUAR, 40_000_000.0));

        Result result = industry.calculate(settings(JAGUAR_BLUEPRINT, true));

        assertTrue(result.invented());
        List<Option> options = result.options();
        assertEquals(Arrays.asList(null, ACCELERANT, ATTAINMENT), options.stream().map(Option::decryptor).toList());
        assertEquals(List.of(10, 11, 14), options.stream().map(option -> option.invention().runsPerCopy()).toList());
        assertEquals(List.of(2, 4, 1), options.stream().map(option -> option.invention().me()).toList());
        assertEquals(List.of(4, 14, 8), options.stream().map(option -> option.invention().te()).toList());
        assertEquals(2, result.bestIndex(), "with free materials, the best chance and the most runs win");
    }

    @Test
    void aBlueprintMissingFromTheCatalogIsRefused() {
        assertThrows(IllegalStateException.class, () -> industry.calculate(settings(RIFTER_BLUEPRINT, false)));
    }
}
