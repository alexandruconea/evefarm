package com.evefarm.service;

import com.evefarm.model.EveCharacter;
import com.evefarm.model.LpOfferRow;
import com.evefarm.model.SellingFees;
import com.evefarm.model.StandingRow;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SellingFeesServiceTest {

    private static final long MALPAIS = 2124165849L;
    private static final SellingFees FEES = new SellingFees("Malpais Legate", 0.03375, 0.0109);

    private static LpOfferRow offer(Double sellPrice, Double buyPrice, Double requiredItemsCost) {
        return new LpOfferRow(1, 34, "Caldari Navy Cloaking Device", "Module", 10, 100, 2_000, 0, "", List.of(),
                requiredItemsCost, "", List.of(), 0.0, sellPrice, buyPrice, null, null, null, null, 34, 10, null,
                null, null);
    }

    @Test
    void sellingThroughASellOrderPaysTheSalesTaxAndTheBrokerFee() {
        LpOfferRow row = SellingFeesService.afterFees(offer(1_000.0, 900.0, 1_000.0), FEES);

        assertEquals(10_000 - 337.5 - 109 - 2_000 - 1_000, row.netProfitSell(), 1e-9);
        assertEquals((10_000 - 337.5 - 109 - 2_000 - 1_000) / 100, row.netIskPerLpSell(), 1e-9);
    }

    @Test
    void sellingToBuyOrdersPaysOnlyTheSalesTax() {
        LpOfferRow row = SellingFeesService.afterFees(offer(1_000.0, 900.0, 1_000.0), FEES);

        assertEquals(9_000 - 303.75 - 2_000 - 1_000, row.netProfitBuy(), 1e-9);
        assertEquals((9_000 - 303.75 - 2_000 - 1_000) / 100, row.netIskPerLpBuy(), 1e-9);
    }

    @Test
    void theBrokerFeeIsAtLeastOneHundredIsk() {
        LpOfferRow row = SellingFeesService.afterFees(offer(10.0, null, 0.0), FEES);

        assertEquals(100 - 3.375 - 100 - 2_000, row.netProfitSell(), 1e-9);
        assertNull(row.netProfitBuy(), "no buy orders, no price");
    }

    @Test
    void anOfferWithAnUnpricedItemHasNoNetValue() {
        LpOfferRow row = SellingFeesService.afterFees(offer(1_000.0, 900.0, null), FEES);

        assertNull(row.netProfitSell());
        assertNull(row.netIskPerLpBuy());
    }

    @Test
    void theMainCharactersStandingsWithJitasOwnerLowerTheBrokerFee() {
        CharacterService characters = mock(CharacterService.class);
        SkillService skills = mock(SkillService.class);
        StandingService standings = mock(StandingService.class);
        when(characters.listCharactersMainFirst()).thenReturn(List.of(
                new EveCharacter(MALPAIS, "Malpais Legate", List.of(), Instant.EPOCH)));
        when(skills.skills(MALPAIS)).thenReturn(Optional.empty());
        when(standings.getRows()).thenReturn(List.of(
                standing(MALPAIS, SellingFeesService.JITA_STATION_FACTION, 7.436762069),
                standing(MALPAIS, SellingFeesService.JITA_STATION_CORPORATION, 9.401696114),
                standing(2122998456L, SellingFeesService.JITA_STATION_FACTION, -10)));

        SellingFees fees = new SellingFeesService(characters, skills, standings).mainCharacterFees();

        assertEquals("Malpais Legate", fees.characterName());
        assertEquals(0.075 * (1 - 0.44), fees.salesTax(), 1e-12, "skills not read yet count as level IV");
        assertEquals(0.03 - 0.012 - 0.0003 * 7.436762069 - 0.0002 * 9.401696114, fees.brokerFee(), 1e-12);
    }

    @Test
    void withoutCharactersTheSkillsCountAsLevelFour() {
        CharacterService characters = mock(CharacterService.class);
        when(characters.listCharactersMainFirst()).thenReturn(List.of());

        SellingFees fees = new SellingFeesService(characters, mock(SkillService.class), mock(StandingService.class))
                .mainCharacterFees();

        assertNull(fees.characterName());
        assertEquals(SellingFeesService.fees(null, skill -> 4, Map.of()), fees);
    }

    private static StandingRow standing(long characterId, long fromId, double standing) {
        return new StandingRow(characterId, "", fromId, "npc_corp", "", standing, null, null, null, null, null);
    }
}
