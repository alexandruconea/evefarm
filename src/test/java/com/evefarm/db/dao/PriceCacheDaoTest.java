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
        assertEquals(Map.of(), dao.findAllBuyVolumes());
    }
}
