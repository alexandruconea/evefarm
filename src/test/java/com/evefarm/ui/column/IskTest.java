package com.evefarm.ui.column;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IskTest {

    @Test
    void anAmountShowsWithoutTheUnitAndSortsByValue() {
        List<Isk> amounts = new ArrayList<>(List.of(Isk.of(1_500_000.5), Isk.NONE, Isk.of(-88_695), Isk.of(990)));

        amounts.sort(null);

        assertEquals(List.of("", "-88,695.00", "990.00", "1,500,000.50"), amounts.stream().map(Isk::toString).toList());
        assertEquals("", Isk.of((Double) null).toString());
    }

    @Test
    void onlySignedAmountsAreGainsOrLosses() {
        assertTrue(Isk.signed(26_378_176.31).isGain());
        assertTrue(Isk.signed(-900_000).isLoss());
        assertFalse(Isk.signed(0).isGain() || Isk.signed(0).isLoss());
        assertFalse(Isk.of(-900_000).isLoss(), "a balance or a price is never red");
        assertTrue(Isk.signed((Double) null).isEmpty());
    }

    @Test
    void theUnitGoesInTheHeaderOnce() {
        assertEquals("Amount (ISK)", Isk.withUnit("Amount"));
        assertEquals("Mission ISK", Isk.withUnit("Mission ISK"));
        assertEquals("ISK/h", Isk.withUnit("ISK/h"));
        assertEquals("Profit (Sell, ISK)", Isk.withUnit("Profit (Sell)"));
    }
}
