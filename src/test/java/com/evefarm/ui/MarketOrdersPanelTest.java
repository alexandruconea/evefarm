package com.evefarm.ui;

import com.evefarm.model.MarketOrderRow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketOrdersPanelTest {

    private static MarketOrderRow order(boolean buy, double price, long remaining, Double brokerFee) {
        return order(buy, MarketOrderRow.ACTIVE, price, remaining, brokerFee);
    }

    private static MarketOrderRow order(boolean buy, String state, double price, long remaining, Double brokerFee) {
        return new MarketOrderRow(1, 1, "Pilot", 34, "Tritanium", null, null, buy, state, price, remaining,
                remaining * 2, null, 60003760L, "Jita", null, null, null, null, 0, null, null, null, null, null,
                null, null, brokerFee, null);
    }

    @Test
    void theSummaryShowsWhatTheRemainingItemsAreWorth() {
        String summary = MarketOrdersPanel.summary(List.of(
                order(false, 1_000, 5, 150.0),
                order(false, 2.5, 100, null)));

        assertEquals("2 orders · Total value: 5,250.00 ISK · Total broker's fees: 150.00 ISK", summary);
    }

    @Test
    void sellAndBuyOrdersAreTotalledSeparately() {
        String summary = MarketOrdersPanel.summary(List.of(
                order(false, 1_000, 5, 10.0),
                order(true, 20, 10, 5.0)));

        assertEquals("2 orders · Total value: 5,000.00 ISK sell, 200.00 ISK buy · Total broker's fees: 15.00 ISK",
                summary);
    }

    @Test
    void closedOrdersAreCountedButNotAddedToTheTotals() {
        String summary = MarketOrdersPanel.summary(List.of(
                order(false, 1_000, 5, 10.0),
                order(false, "expired", 9_999, 0, 50.0),
                order(false, "cancelled", 9_999, 3, 50.0)));

        assertEquals("1 active order · Total value: 5,000.00 ISK · Total broker's fees: 10.00 ISK · 2 closed orders",
                summary);
    }

    @Test
    void noOrdersMeansNothingIsListed() {
        assertEquals("0 orders · Total value: 0.00 ISK · Total broker's fees: 0.00 ISK",
                MarketOrdersPanel.summary(List.of()));
    }

    @Test
    void aClosedOrderSaysHowItEnded() {
        assertEquals("Fulfilled", MarketOrdersTableModel.statusText(order(false, "expired", 10, 0, null)));
        assertEquals("Expired", MarketOrdersTableModel.statusText(order(false, "expired", 10, 4, null)));
        assertEquals("Cancelled", MarketOrdersTableModel.statusText(order(true, "cancelled", 10, 4, null)));
        assertEquals("Active", MarketOrdersTableModel.statusText(order(true, 10, 4, null)));
    }
}
