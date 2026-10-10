package com.evefarm.service;

import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.db.dao.DailyVolumeDao;
import com.evefarm.esi.EsiException;
import com.evefarm.esi.MarketsApi;
import com.evefarm.esi.dto.MarketHistoryDto;
import com.evefarm.model.LpOfferRow;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static com.evefarm.service.MarketHistoryService.THE_FORGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketHistoryServiceTest {

    private static final int JAGUAR = 11377;
    private static final int OLD_ITEM = 600;

    @Test
    void theDailyVolumeIsTheAverageOfTheLast30PublishedDays() {
        LocalDate lastDay = LocalDate.parse("2026-10-08");
        List<MarketHistoryDto> history = List.of(
                new MarketHistoryDto("2026-09-08", 9_000),
                new MarketHistoryDto("2026-09-09", 30),
                new MarketHistoryDto("2026-10-07", 20),
                new MarketHistoryDto("2026-10-08", 10),
                new MarketHistoryDto("2026-10-09", 5_000));

        assertEquals(2.0, MarketHistoryService.dailyVolume(history, lastDay), 1e-9);
    }

    @Test
    void esiPublishesTheHistoryEveryDayAt1105Utc() {
        assertEquals(Instant.parse("2026-10-08T11:05:00Z"),
                MarketHistoryService.lastRefresh(Instant.parse("2026-10-09T11:04:59Z")));
        assertEquals(Instant.parse("2026-10-09T11:05:00Z"),
                MarketHistoryService.lastRefresh(Instant.parse("2026-10-09T11:05:00Z")));
    }

    @Test
    void volumesAreAskedForOnceADayAndUnknownItemsCountAsNoSales() {
        Database database = new Database(":memory:");
        MigrationRunner.run(database);
        MarketsApi markets = mock(MarketsApi.class);
        when(markets.listRegionHistory(THE_FORGE, JAGUAR)).thenReturn(List.of(
                new MarketHistoryDto("2026-10-08", 60)));
        when(markets.listRegionHistory(THE_FORGE, OLD_ITEM)).thenThrow(new EsiException(404, "Type not found"));
        Clock morning = Clock.fixed(Instant.parse("2026-10-09T12:00:00Z"), ZoneOffset.UTC);
        Clock evening = Clock.fixed(Instant.parse("2026-10-09T20:00:00Z"), ZoneOffset.UTC);
        DailyVolumeDao dao = new DailyVolumeDao(database);

        List<Integer> types = List.of(JAGUAR, OLD_ITEM);

        Map<Integer, Double> first = new MarketHistoryService(markets, dao, morning).dailyVolumes(types);
        Map<Integer, Double> again = new MarketHistoryService(markets, dao, evening).dailyVolumes(types);

        assertEquals(Map.of(JAGUAR, 2.0, OLD_ITEM, 0.0), first);
        assertEquals(first, again);
        verify(markets, times(1)).listRegionHistory(THE_FORGE, JAGUAR);
    }

    @Test
    void daysToSellIsTheOffersUnitsOverTheDailyVolume() {
        LpOfferRow row = new LpOfferRow(1, JAGUAR, "Jaguar", "Ship", 5, 1000, 0, 0, "", List.of(), null, "",
                List.of(), null, null, null, null, null, null, null, JAGUAR, 5, null, null);

        assertNull(row.daysToSell());
        assertEquals(2.5, row.withDailyVolume(2.0).daysToSell(), 1e-9);
        assertTrue(row.withDailyVolume(0.0).daysToSell().isInfinite());
    }
}
