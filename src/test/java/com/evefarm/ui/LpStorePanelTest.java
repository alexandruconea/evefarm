package com.evefarm.ui;

import com.evefarm.model.LpNetValues;
import com.evefarm.model.LpOfferRow;
import com.evefarm.model.SellingFees;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LpStorePanelTest {

    @Test
    void theLineUnderTheTableSaysWhoseFeesTheNetColumnsUse() {
        assertEquals("Net columns: Malpais Legate, sales tax 3.375%, broker fee 1.089%",
                LpStorePanel.feesText(new SellingFees("Malpais Legate", 0.075 * (1 - 0.55), 0.0108886322)));
        assertEquals("Net columns: skills at level IV, sales tax 4.2%, broker fee 1.8%",
                LpStorePanel.feesText(new SellingFees(null, 0.042, 0.018)));
    }

    @Test
    void theNetIskPerLpColumnsAreColoredLikeTheOthers() {
        LpOfferRow row = new LpOfferRow(1, 34, "Item", "Module", 1, 100, 0, 0, "", List.of(), 0.0, "", List.of(),
                0.0, 10.0, 9.0, 2_538.0, 2_045.0, null, null, 34, 1, null, null,
                new LpNetValues(2_390.0, 1_970.0, null, null));

        assertEquals(2_538.0, LpStorePanel.iskPerLp(row, "iskPerLpSell"));
        assertEquals(2_045.0, LpStorePanel.iskPerLp(row, "iskPerLpBuy"));
        assertEquals(2_390.0, LpStorePanel.iskPerLp(row, "netIskPerLpSell"));
        assertEquals(1_970.0, LpStorePanel.iskPerLp(row, "netIskPerLpBuy"));
        assertNull(LpStorePanel.iskPerLp(row, "netProfitSell"));
    }
}
