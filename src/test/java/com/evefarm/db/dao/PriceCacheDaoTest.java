package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.model.PriceBreakdown;
import com.evefarm.model.PriceMode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PriceCacheDaoTest {

    @Test
    void missingAndOldPricesAreStale() {
        Database database = new Database(":memory:");
        MigrationRunner.run(database);
        PriceCacheDao dao = new PriceCacheDao(database);
        dao.replaceAll(Map.of(36, new PriceCacheDao.AverageAdjusted(60.0, 55.0)));
        dao.replaceAllDetailed(Map.of(34, new PriceBreakdown(5.0, 4.5, 4.4, 4.2, 4.0, 3.9, 3.8, 3.7, 3.6, 3.5,
                1e9, 1e9)));

        assertEquals(Set.of(35), dao.findStaleTypeIds(List.of(34, 35, 36), Instant.now().minus(Duration.ofHours(1))));
        assertEquals(Set.of(34, 35, 36), dao.findStaleTypeIds(List.of(34, 35, 36),
                Instant.now().plus(Duration.ofMinutes(1))));
    }

    @Test
    void aSideWithoutOrdersHasNoPriceWhenThatSideIsAskedFor() {
        Database database = new Database(":memory:");
        MigrationRunner.run(database);
        PriceCacheDao dao = new PriceCacheDao(database);
        dao.replaceAllDetailed(Map.of(
                494, new PriceBreakdown(450_000.0, 456_958.8, 450_000.0, 444_000.0, 443_900.0,
                        0.0, 0.0, 0.0, 0.0, 0.0, 30.0, 0.0),
                3081, new PriceBreakdown(0.0, 0.0, 0.0, 0.0, 0.0,
                        57_740_000.0, 21_114_147.0, 20_000_000.0, 50_000_000.0, 1_000_000.0, 0.0, 12.0)));

        assertTrue(dao.findMarketSidePrice(494, PriceMode.BUY_MAX).isEmpty(), "nobody buys it");
        assertEquals(443_900.0, dao.findMarketSidePrice(494, PriceMode.SELL_MIN).orElseThrow(), 1e-9);
        assertTrue(dao.findMarketSidePrice(3081, PriceMode.SELL_MIN).isEmpty(), "nobody sells it");
        assertEquals(Map.of(494, 443_900.0), dao.findAllMarketSidePrices(PriceMode.SELL_MIN));
        assertEquals(Map.of(3081, 57_740_000.0), dao.findAllMarketSidePrices(PriceMode.BUY_MAX));
        assertEquals(456_958.8, dao.findUnitPrice(494, PriceMode.BUY_MAX).orElseThrow(), 1e-9,
                "valuing an item still falls back to any price");
    }

    @Test
    void withCcpPricesEverySideIsTheAveragePrice() {
        Database database = new Database(":memory:");
        MigrationRunner.run(database);
        PriceCacheDao dao = new PriceCacheDao(database);
        dao.replaceAll(Map.of(34, new PriceCacheDao.AverageAdjusted(5.0, 4.0)));

        assertEquals(5.0, dao.findMarketSidePrice(34, PriceMode.SELL_MIN).orElseThrow(), 1e-9);
        assertEquals(5.0, dao.findMarketSidePrice(34, PriceMode.BUY_MAX).orElseThrow(), 1e-9);
    }

    @Test
    void aCcpRefreshReplacesThePricesOfAnotherProvider() {
        Database database = new Database(":memory:");
        MigrationRunner.run(database);
        PriceCacheDao dao = new PriceCacheDao(database);
        dao.replaceAllDetailed(Map.of(34, new PriceBreakdown(null, 100.0, null, null, null, null, 80.0, null, null,
                null, 1e9, 1e9)));

        dao.replaceAll(Map.of(34, new PriceCacheDao.AverageAdjusted(5.0, 4.0)));

        assertEquals(5.0, dao.findUnitPrice(34, PriceMode.SELL_AVG).orElseThrow(), 1e-9);
        assertEquals(5.0, dao.findUnitPrice(34, PriceMode.BUY_AVG).orElseThrow(), 1e-9);
        assertEquals(Map.of(), dao.findAllSellVolumes());
    }
}
