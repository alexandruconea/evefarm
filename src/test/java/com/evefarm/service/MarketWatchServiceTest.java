package com.evefarm.service;

import com.evefarm.auth.AuthService;
import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.db.dao.OrderCompetitionDao;
import com.evefarm.esi.MarketsApi;
import com.evefarm.esi.dto.MarketOrderDto;
import com.evefarm.model.EveCharacter;
import com.evefarm.model.OrderCompetition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketWatchServiceTest {

    private static final long PILOT = 42L;
    private static final long ALT = 43L;
    private static final long JITA = 60003760L;
    private static final long AMARR = 60008494L;
    private static final long FORGE = 10000002L;
    private static final int TRITANIUM = 34;
    private static final Instant NOW = Instant.parse("2026-10-09T12:00:00Z");

    private final AuthService auth = mock(AuthService.class);
    private final MarketsApi markets = mock(MarketsApi.class);
    private OrderCompetitionDao dao;

    @BeforeEach
    void setUp() {
        Database database = new Database(":memory:");
        MigrationRunner.run(database);
        dao = new OrderCompetitionDao(database);
        when(auth.getValidAccessToken(PILOT)).thenReturn("token");
        when(auth.getValidAccessToken(ALT)).thenReturn("alt-token");
    }

    private MarketWatchService watch(Instant now) {
        return new MarketWatchService(auth, markets, dao, Clock.fixed(now, ZoneOffset.UTC));
    }

    private static EveCharacter character(long id) {
        return new EveCharacter(id, "Pilot " + id, List.of(), NOW);
    }

    private static MarketOrderDto order(long orderId, boolean buy, double price, long location) {
        return new MarketOrderDto(orderId, TRITANIUM, 100, 100, price, buy, null, null, location, null, 90, "station",
                1L, FORGE);
    }

    @Test
    void competitorsAreOtherPeoplesOrdersOnTheSameSideAtTheSameStation() {
        MarketOrderDto sell = order(1, false, 5.00, JITA);
        MarketOrderDto buy = order(2, true, 5.00, JITA);

        assertEquals(OptionalDouble.of(4.99), MarketWatchService.bestCompetitor(sell,
                List.of(order(3, false, 4.99, JITA), order(4, false, 6.00, JITA)), Set.of(1L, 2L)));
        assertTrue(MarketWatchService.bestCompetitor(sell, List.of(order(3, false, 4.00, AMARR)), Set.of())
                .isEmpty());
        assertTrue(MarketWatchService.bestCompetitor(sell, List.of(order(3, false, 4.00, JITA)), Set.of(3L))
                .isEmpty());
        assertTrue(MarketWatchService.bestCompetitor(sell, List.of(order(3, true, 6.00, JITA)), Set.of())
                .isEmpty());
        assertEquals(OptionalDouble.of(5.01), MarketWatchService.bestCompetitor(buy,
                List.of(order(3, true, 5.01, JITA), order(4, true, 4.00, JITA)), Set.of()));
    }

    @Test
    void onlyAStrictlyBetterPriceOutbidsAnOrder() {
        assertTrue(MarketWatchService.beats(order(1, false, 5.00, JITA), 4.99));
        assertFalse(MarketWatchService.beats(order(1, false, 5.00, JITA), 5.00));
        assertTrue(MarketWatchService.beats(order(1, true, 5.00, JITA), 5.01));
        assertFalse(MarketWatchService.beats(order(1, true, 5.00, JITA), 4.00));
    }

    @Test
    void everyOrderIsCheckedAndTheOutbidOnesAreReported() {
        MarketOrderDto beaten = order(100, false, 10.0, JITA);
        MarketOrderDto leading = order(101, true, 3.0, JITA);
        MarketOrderDto alone = order(102, false, 10.0, AMARR);
        MarketOrderDto alts = order(200, false, 9.0, JITA);
        when(markets.listCharacterOrders(PILOT, "token")).thenReturn(List.of(beaten, leading, alone));
        when(markets.listCharacterOrders(ALT, "alt-token")).thenReturn(List.of(alts));
        when(markets.listRegionOrders(FORGE, TRITANIUM)).thenReturn(List.of(beaten, leading, alone, alts,
                order(300, false, 9.5, JITA), order(301, true, 2.0, JITA)));
        AtomicInteger heard = new AtomicInteger();
        MarketWatchService watch = watch(NOW);
        watch.addListener(heard::incrementAndGet);

        Map<EveCharacter, List<MarketWatchService.Outbid>> outbid = watch.check(List.of(character(PILOT),
                character(ALT)));

        assertEquals(List.of(new MarketWatchService.Outbid(beaten, 9.5)), outbid.get(character(PILOT)));
        assertEquals(List.of(), outbid.get(character(ALT)), "an alt's cheaper order doesn't count");
        Map<Long, OrderCompetition> saved = watch.latestResults();
        assertEquals(new OrderCompetition(100, 9.5, true, NOW), saved.get(100L));
        assertEquals(new OrderCompetition(101, 2.0, false, NOW), saved.get(101L));
        assertEquals(new OrderCompetition(102, null, false, NOW), saved.get(102L));
        assertEquals(new OrderCompetition(200, 9.5, false, NOW), saved.get(200L));
        verify(markets, times(1)).listRegionOrders(FORGE, TRITANIUM);
        assertEquals(1, heard.get());
    }

    @Test
    void aCharacterThatCannotBeReadKeepsItsLastResults() {
        when(markets.listCharacterOrders(PILOT, "token")).thenReturn(List.of(order(100, false, 10.0, JITA)));
        when(markets.listRegionOrders(FORGE, TRITANIUM)).thenReturn(List.of(order(300, false, 9.5, JITA)));
        watch(NOW).check(List.of(character(PILOT)));
        when(markets.listCharacterOrders(PILOT, "token")).thenThrow(new IllegalStateException("ESI is down"));

        MarketWatchService later = watch(NOW.plus(Duration.ofMinutes(15)));

        assertTrue(later.check(List.of(character(PILOT))).isEmpty());
        assertTrue(later.latestResults().get(100L).outbid());
    }

    @Test
    void resultsOlderThanAnHourAreNotShown() {
        when(markets.listCharacterOrders(PILOT, "token")).thenReturn(List.of(order(100, false, 10.0, JITA)));
        when(markets.listRegionOrders(FORGE, TRITANIUM)).thenReturn(List.of());
        watch(NOW).check(List.of(character(PILOT)));

        assertEquals(Set.of(100L), watch(NOW.plus(Duration.ofMinutes(59))).latestResults().keySet());
        assertTrue(watch(NOW.plus(Duration.ofMinutes(61))).latestResults().isEmpty());
    }
}
