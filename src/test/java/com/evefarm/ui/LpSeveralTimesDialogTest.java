package com.evefarm.ui;

import com.evefarm.model.LpOfferRow;
import com.evefarm.model.LpRequiredItem;
import com.evefarm.service.LpOfferDepthService.Purchase;
import com.evefarm.service.LpOfferDepthService.Result;
import com.evefarm.service.LpOfferDepthService.Sale;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LpSeveralTimesDialogTest {

    private static final LpRequiredItem TAGS = new LpRequiredItem(17_202, "Imperial Navy Major General Insignia", 10);
    private static final Sale SOLD = new Sale(5, 5, 40_000_000.0, 1.0, 1.0, 1.0, 1.0);

    @Test
    void theLpHintSaysHowManyTimesYourLpIsEnoughFor() {
        assertEquals("Your LP is enough for 12", LpSeveralTimesDialog.lpHint(125_000, 10_000));
        assertEquals("Your LP isn't enough for one", LpSeveralTimesDialog.lpHint(9_999, 10_000));
        assertEquals("", LpSeveralTimesDialog.lpHint(0, 10_000));
        assertEquals("", LpSeveralTimesDialog.lpHint(125_000, 0));
    }

    @Test
    void theOfferLineSaysWhatABlueprintBuilds() {
        assertEquals("One offer costs 10,000 LP and 1,000,000.00 ISK",
                LpSeveralTimesDialog.perOfferText(offer(List.of()), null));
        assertEquals("One offer costs 10,000 LP and 1,000,000.00 ISK and builds 1 x Imperial Navy Heat Sink",
                LpSeveralTimesDialog.perOfferText(offer(List.of(TAGS)), "Imperial Navy Heat Sink"));
        assertEquals("One offer costs 10,000 LP and 1,000,000.00 ISK and gives 1 x Imperial Navy Heat Sink",
                LpSeveralTimesDialog.perOfferText(offer(List.of()), "Imperial Navy Heat Sink"));
    }

    @Test
    void theCostLineAddsTheItemsToTheIsk() {
        Purchase bought = new Purchase(TAGS, false, 50, 130, 1_000_000.0, 50, 56_000_000);

        assertEquals("Cost: 50,000 LP + 5,000,000.00 ISK + 56,000,000.00 ISK for the items = 61,000,000.00 ISK",
                LpSeveralTimesDialog.costText(new Result(50_000, 5_000_000, List.of(bought), 56_000_000.0,
                        SOLD, SOLD)));
        assertEquals("Cost: 50,000 LP + 5,000,000.00 ISK, and Jita 4-4 hasn't all the items",
                LpSeveralTimesDialog.costText(new Result(50_000, 5_000_000, List.of(bought), null, SOLD, SOLD)));
        assertEquals("Cost: 50,000 LP + 5,000,000.00 ISK",
                LpSeveralTimesDialog.costText(new Result(50_000, 5_000_000, List.of(), 0.0, SOLD, SOLD)));
    }

    @Test
    void theWarningsNameWhatJitaHasNotEnoughOf() {
        Purchase few = new Purchase(TAGS, false, 50, 30, 1_000_000.0, 30, 30_000_000);
        Purchase none = new Purchase(new LpRequiredItem(34, "Tritanium", 1_000), true, 5_000, 0, null, 0, 0);
        Sale noSellers = new Sale(5, 5, null, null, null, null, null);
        Sale fewBuyers = new Sale(5, 2, 30_000_000.0, null, null, null, null);

        assertEquals("Jita 4-4 has only 30 Imperial Navy Major General Insignia for sale, you need 50.\n"
                        + "Nobody sells Tritanium in Jita 4-4.\n"
                        + "Nobody sells Imperial Navy Heat Sink in Jita 4-4, so there is no lowest sell price.\n"
                        + "The buy orders that reach Jita 4-4 take only 2 of the 5 Imperial Navy Heat Sink.",
                LpSeveralTimesDialog.warnings(new Result(50_000, 5_000_000, List.of(few, none), null, noSellers,
                        fewBuyers), "Imperial Navy Heat Sink"));
        assertEquals("", LpSeveralTimesDialog.warnings(new Result(50_000, 5_000_000, List.of(), 0.0, SOLD, SOLD),
                "Imperial Navy Heat Sink"));
    }

    @Test
    void daysToSellUseTheUnitsOfAllTheOffers() {
        assertEquals("2.5", LpSeveralTimesDialog.daysToSell(5, 2.0));
        assertEquals("No sales", LpSeveralTimesDialog.daysToSell(5, 0.0));
        assertEquals("", LpSeveralTimesDialog.daysToSell(5, null));
    }

    private static LpOfferRow offer(List<LpRequiredItem> materials) {
        return new LpOfferRow(1, 17_627, "Imperial Navy Heat Sink Blueprint", "Blueprint", 1, 10_000, 1_000_000, 0,
                "", List.of(), 0.0, "", materials, 0.0, null, null, null, null, null, null, 15_810, 1, null, null,
                null);
    }
}
