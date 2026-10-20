package com.evefarm.service;

import com.evefarm.esi.EsiException;
import com.evefarm.esi.MarketsApi;
import com.evefarm.esi.dto.MarketOrderDto;
import com.evefarm.model.LpOfferRow;
import com.evefarm.model.LpRequiredItem;
import com.evefarm.model.SellingFees;
import com.evefarm.service.LpOfferDepthService.JitaOrders;
import com.evefarm.service.LpOfferDepthService.Purchase;
import com.evefarm.service.LpOfferDepthService.Result;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LpOfferDepthServiceTest {

    private static final long FORGE = MarketHistoryService.THE_FORGE;
    private static final long JITA_4_4 = LpOfferDepthService.JITA_4_4;
    private static final long JITA = LpOfferDepthService.JITA;
    private static final long JITA_OTHER_STATION = 60_003_466L;
    private static final long PERIMETER_STATION = 60_000_361L;
    private static final long PERIMETER = 30_000_144L;
    private static final int HEAT_SINK_BLUEPRINT = 17_627;
    private static final int HEAT_SINK = 15_810;
    private static final int TAG = 17_202;
    private static final int TRITANIUM = 34;
    private static final SellingFees FEES = new SellingFees("Seller", 0.05, 0.02);

    @Test
    void theItemsAreBoughtFromTheCheapestSellOrderInJita44Up() {
        JitaOrders orders = orders(Map.of(
                TAG, List.of(sell(1, 1_300_000, 100, JITA_4_4), sell(2, 1_000_000, 30, JITA_4_4),
                        sell(3, 500_000, 500, JITA_OTHER_STATION), buy(4, 2_000_000, 500, JITA_4_4, "region", 1L))));

        Purchase tags = LpOfferDepthService.price(offer(List.of(tag(10)), List.of()), 5, orders, FEES)
                .purchases().getFirst();

        assertEquals(50, tags.needed());
        assertEquals(130, tags.forSale());
        assertEquals(50, tags.bought());
        assertTrue(tags.enough());
        assertEquals(1_000_000, tags.lowestPrice(), 1e-9);
        assertEquals(30 * 1_000_000 + 20 * 1_300_000, tags.cost(), 1e-9);
        assertEquals(1_120_000, tags.averagePrice(), 1e-9);
        assertEquals(6_000_000, tags.aboveLowest(), 1e-9);
    }

    @Test
    void oneOfferAtTheLowestPriceCostsWhatTheLpStoreTableShows() {
        JitaOrders orders = orders(Map.of(TAG, List.of(sell(1, 1_000_000, 30, JITA_4_4),
                sell(2, 1_300_000, 100, JITA_4_4))));

        Purchase tags = LpOfferDepthService.price(offer(List.of(tag(10)), List.of()), 1, orders, FEES)
                .purchases().getFirst();

        assertEquals(10_000_000, tags.cost(), 1e-9);
        assertEquals(0, tags.aboveLowest(), 1e-9);
    }

    @Test
    void anItemNeededTwiceIsBoughtOnceFromTheSameOrders() {
        JitaOrders orders = orders(Map.of(TRITANIUM, List.of(sell(1, 4, 1_000, JITA_4_4),
                sell(2, 5, 10_000, JITA_4_4))));
        LpOfferRow offer = offer(List.of(new LpRequiredItem(TRITANIUM, "Tritanium", 600)),
                List.of(new LpRequiredItem(TRITANIUM, "Tritanium", 600)));

        List<Purchase> purchases = LpOfferDepthService.price(offer, 1, orders, FEES).purchases();

        assertEquals(600 * 4.0, purchases.get(0).cost(), 1e-9);
        assertEquals(400 * 4.0 + 200 * 5.0, purchases.get(1).cost(), 1e-9);
        assertEquals(600, purchases.get(1).bought());
        assertEquals(200, purchases.get(1).aboveLowest(), 1e-9);
    }

    @Test
    void profitAndIskPerLpCountAllTheOffersAtTheirRealCost() {
        JitaOrders orders = orders(Map.of(
                TAG, List.of(sell(1, 1_000_000, 30, JITA_4_4), sell(2, 1_300_000, 100, JITA_4_4)),
                TRITANIUM, List.of(sell(3, 4, 1_000_000, JITA_4_4)),
                HEAT_SINK, List.of(sell(4, 40_000_000, 3, JITA_4_4), sell(5, 45_000_000, 10, JITA_4_4),
                        buy(6, 30_000_000, 3, JITA_4_4, "station", 1L),
                        buy(7, 28_000_000, 10, JITA_4_4, "station", 1L))));
        LpOfferRow offer = offer(List.of(tag(10)), List.of(new LpRequiredItem(TRITANIUM, "Tritanium", 1_000)));

        Result result = LpOfferDepthService.price(offer, 5, orders, FEES);

        double other = 56_000_000 + 5_000 * 4;
        assertEquals(other, result.otherCost(), 1e-6);
        assertEquals(50_000, result.lp());
        assertEquals(5_000_000, result.isk(), 1e-9);
        assertEquals(5, result.sellOrder().units());
        assertEquals(40_000_000, result.sellOrder().unitPrice(), 1e-9);
        double sellProfit = 5 * 40_000_000.0 - 5_000_000 - other;
        assertEquals(sellProfit, result.sellOrder().profit(), 1e-6);
        assertEquals(sellProfit / 50_000, result.sellOrder().iskPerLp(), 1e-9);
        assertEquals(sellProfit - 200_000_000 * (0.05 + 0.02), result.sellOrder().netProfit(), 1e-6);
        double income = 3 * 30_000_000.0 + 2 * 28_000_000.0;
        assertEquals(income / 5, result.buyOrders().unitPrice(), 1e-9);
        assertEquals(income - 5_000_000 - other, result.buyOrders().profit(), 1e-6);
        assertEquals(income * 0.95 - 5_000_000 - other, result.buyOrders().netProfit(), 1e-6);
        assertEquals((income * 0.95 - 5_000_000 - other) / 50_000, result.buyOrders().netIskPerLp(), 1e-9);
    }

    @Test
    void withoutEnoughItemsInJitaThereIsNoProfit() {
        JitaOrders orders = orders(Map.of(
                TAG, List.of(sell(1, 1_000_000, 30, JITA_4_4)),
                HEAT_SINK, List.of(sell(2, 40_000_000, 3, JITA_4_4), buy(3, 30_000_000, 10, JITA_4_4, "station", 1L))));

        Result result = LpOfferDepthService.price(offer(List.of(tag(10)), List.of()), 5, orders, FEES);

        Purchase tags = result.purchases().getFirst();
        assertFalse(tags.enough());
        assertEquals(30, tags.bought());
        assertEquals(30_000_000, tags.cost(), 1e-9);
        assertNull(result.otherCost());
        assertNull(result.sellOrder().profit());
        assertNull(result.buyOrders().netIskPerLp());
    }

    @Test
    void buyOrdersThatCantTakeEverythingLeaveNoBuyProfit() {
        JitaOrders orders = orders(Map.of(HEAT_SINK, List.of(sell(1, 40_000_000, 1, JITA_4_4),
                buy(2, 30_000_000, 2, JITA_4_4, "station", 1L))));

        Result result = LpOfferDepthService.price(offer(List.of(), List.of()), 5, orders, FEES);

        assertEquals(0.0, result.otherCost());
        assertTrue(result.sellOrder().enough());
        assertEquals(5 * 40_000_000.0 - 5_000_000, result.sellOrder().profit(), 1e-6);
        assertFalse(result.buyOrders().enough());
        assertEquals(2, result.buyOrders().sold());
        assertEquals(30_000_000, result.buyOrders().unitPrice(), 1e-9);
        assertNull(result.buyOrders().profit());
    }

    @Test
    void onlyBuyOrdersThatReachJita44Count() {
        assertTrue(LpOfferDepthService.reachesJita44(buy(1, 1, 1, JITA_4_4, "station", 1L)));
        assertTrue(LpOfferDepthService.reachesJita44(buy(2, 1, 1, PERIMETER_STATION, "region", 1L)));
        assertTrue(LpOfferDepthService.reachesJita44(buy(3, 1, 1, JITA_OTHER_STATION, "solarsystem", 1L)));
        assertTrue(LpOfferDepthService.reachesJita44(buy(4, 1, 1, JITA_OTHER_STATION, "5", 1L)));
        assertFalse(LpOfferDepthService.reachesJita44(buy(5, 1, 1, JITA_OTHER_STATION, "station", 1L)));
        assertFalse(LpOfferDepthService.reachesJita44(buy(6, 1, 1, PERIMETER_STATION, "solarsystem", 1L)));
    }

    @Test
    void aBuyOrderWithAMinimumVolumeAboveWhatIsLeftIsSkipped() {
        List<MarketOrderDto> best = LpOfferDepthService.buyOrders(List.of(
                buy(1, 30_000_000, 100, JITA_4_4, "station", 10L),
                buy(2, 29_000_000, 100, JITA_4_4, "station", 1L),
                buy(3, 35_000_000, 4, JITA_4_4, "station", 10L)));

        LpOfferDepthService.Fill fill = LpOfferDepthService.fill(best, 5);

        assertEquals(5, fill.units());
        assertEquals(4 * 35_000_000.0 + 29_000_000, fill.isk(), 1e-9);
    }

    @Test
    void readingAsksForSellOrdersOfTheItemsAndAllOrdersOfWhatTheOfferGives() {
        MarketsApi markets = mock(MarketsApi.class);
        when(markets.listRegionOrders(FORGE, TAG, "sell")).thenThrow(new EsiException(404, "Type not found"));
        when(markets.listRegionOrders(FORGE, TRITANIUM, "sell")).thenReturn(List.of(sell(1, 4, 10, JITA_4_4)));
        when(markets.listRegionOrders(FORGE, HEAT_SINK, "all")).thenReturn(List.of(sell(2, 40, 1, JITA_4_4)));

        JitaOrders orders = new LpOfferDepthService(markets).read(
                offer(List.of(tag(10)), List.of(new LpRequiredItem(TRITANIUM, "Tritanium", 1_000))));

        verify(markets).listRegionOrders(FORGE, HEAT_SINK, "all");
        assertEquals(List.of(), orders.byType().get(TAG));
        assertEquals(1, orders.byType().get(TRITANIUM).size());
        assertEquals(1, orders.byType().get(HEAT_SINK).size());
    }

    private static LpRequiredItem tag(long quantity) {
        return new LpRequiredItem(TAG, "Imperial Navy Major General Insignia", quantity);
    }

    private static LpOfferRow offer(List<LpRequiredItem> required, List<LpRequiredItem> materials) {
        return new LpOfferRow(1, HEAT_SINK_BLUEPRINT, "Imperial Navy Heat Sink Blueprint", "Blueprint", 1, 10_000,
                1_000_000, 0, "", required, 0.0, "", materials, 0.0, null, null, null, null, null, null, HEAT_SINK,
                1, null, null, null);
    }

    private static JitaOrders orders(Map<Integer, List<MarketOrderDto>> byType) {
        return new JitaOrders(byType, Instant.EPOCH);
    }

    private static MarketOrderDto sell(long orderId, double price, long units, long location) {
        return new MarketOrderDto(orderId, 0, units, units, price, false, null, null, location, null, 90, "region",
                null, FORGE, location == JITA_4_4 ? JITA : PERIMETER);
    }

    private static MarketOrderDto buy(long orderId, double price, long units, long location, String range,
                                      Long minVolume) {
        return new MarketOrderDto(orderId, 0, units, units, price, true, null, null, location, null, 90, range,
                minVolume, FORGE, location == PERIMETER_STATION ? PERIMETER : JITA);
    }
}
