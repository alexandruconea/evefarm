package com.evefarm.service;

import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.db.dao.CorporationDao;
import com.evefarm.db.dao.CorporationIndustryDao;
import com.evefarm.db.dao.CorporationMarketDao;
import com.evefarm.db.dao.CorporationSnapshotDao;
import com.evefarm.model.ContractEntry;
import com.evefarm.model.Corporation;
import com.evefarm.model.IndustryJobEntry;
import com.evefarm.model.JournalEntry;
import com.evefarm.model.MarketOrderEntry;
import com.evefarm.model.MarketOrderRow;
import com.evefarm.model.TrackerSnapshot;
import com.evefarm.model.TransactionEntry;
import com.evefarm.service.IncomeSources.Source;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.OptionalDouble;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CorporationTrackerTest {

    private static final long CORPORATION = 98_000_001L;
    private static final int PRODUCT = 587;
    private static final int BLUEPRINT = 691;

    private Database database;
    private CorporationDao corporationDao;
    private CorporationMarketDao marketDao;
    private CorporationIndustryDao industryDao;
    private CorporationSnapshotDao snapshotDao;
    private IndustryCatalogService catalog;
    private PriceService prices;
    private Instant now;

    @BeforeEach
    void setUp() {
        database = new Database(":memory:");
        MigrationRunner.run(database);
        corporationDao = new CorporationDao(database);
        corporationDao.saveCorporation(new Corporation(CORPORATION, "Deep Core Mining", "DCM"));
        now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        marketDao = new CorporationMarketDao(database, Clock.fixed(now, ZoneOffset.UTC));
        industryDao = new CorporationIndustryDao(database);
        snapshotDao = new CorporationSnapshotDao(database);
        catalog = mock(IndustryCatalogService.class);
        when(catalog.unitsPerRun(anyInt(), anyInt())).thenReturn(1L);
        prices = mock(PriceService.class);
        when(prices.getUnitPrice(anyInt())).thenReturn(OptionalDouble.of(1_000_000));
    }

    private CorporationTracker tracker(Instant at) {
        return new CorporationTracker(corporationDao, marketDao, industryDao, snapshotDao, catalog, prices,
                Clock.fixed(at, ZoneOffset.UTC));
    }

    private static MarketOrderEntry order(long id, boolean buy, double price, long remaining, Double escrow) {
        return new MarketOrderEntry(id, 34, buy, price, remaining, remaining, escrow, 60003760L,
                "2026-11-27T10:00:00Z", 90, MarketOrderRow.ACTIVE, "station", null);
    }

    private static ContractEntry contract(long id, String type, String status, boolean forCorporation,
                                          Double collateral, Double price, Integer acceptor) {
        return new ContractEntry(id, type, status, null, collateral, price, null, 1.0, "2026-11-27T10:00:00Z",
                "2026-12-11T10:00:00Z", null, forCorporation, 90, null, acceptor, 60003760L, 60003760L);
    }

    private void fillCorporation() {
        corporationDao.saveWallets(CORPORATION, Map.of(1, 5_000_000_000.0, 2, 1_000_000_000.0));
        marketDao.saveOrders(CORPORATION, List.of(
                new CorporationMarketDao.Order(order(1, false, 10_000_000, 10, null), 1),
                new CorporationMarketDao.Order(order(2, true, 1_000_000, 100, 40_000_000.0), 1)));
        marketDao.saveContracts(CORPORATION, List.of(
                contract(1, "item_exchange", "outstanding", true, null, 300_000_000.0, null),
                contract(2, "courier", "in_progress", true, 900_000_000.0, null, null),
                contract(3, "courier", "in_progress", false, 50_000_000.0, null, (int) CORPORATION),
                contract(4, "item_exchange", "outstanding", false, null, 7_000_000_000.0, null),
                contract(5, "item_exchange", "finished", true, null, 8_000_000_000.0, null)));
        industryDao.saveJobs(CORPORATION, List.of(new CorporationIndustryDao.Job(new IndustryJobEntry(1, 1,
                "active", BLUEPRINT, PRODUCT, 20, 0.0, 1L, 1L, "2026-11-27T10:00:00Z", "2026-12-01T10:00:00Z"), 90L)));
    }

    @Test
    void theSnapshotAddsUpWhatTheCorporationOwns() {
        fillCorporation();

        TrackerSnapshot snapshot = tracker(now).snapshot(CORPORATION, now);

        assertTrue(snapshot.corporation());
        assertEquals(CORPORATION, snapshot.characterId());
        assertEquals(6_000_000_000.0, snapshot.walletBalance(), 0.5);
        assertEquals(100_000_000.0, snapshot.sellOrdersValue(), 0.5);
        assertEquals(40_000_000.0, snapshot.escrowValue(), 0.5);
        assertEquals(60_000_000.0, snapshot.escrowToCoverValue(), 0.5);
        assertEquals(20_000_000.0, snapshot.manufacturingValue(), 0.5);
        assertEquals(300_000_000.0, snapshot.contractsValue(), 0.5,
                "only the open contracts issued for the corporation");
        assertEquals(950_000_000.0, snapshot.contractCollateralValue(), 0.5,
                "the courier it issued and the one it carries");
        assertEquals(6_000_000_000.0 + 100_000_000 + 40_000_000 + 20_000_000 + 300_000_000 + 950_000_000,
                snapshot.totalValue(), 0.5, "escrow to cover is shown but not added, as for characters");
    }

    @Test
    void oneSnapshotADayTheNewestKept() {
        fillCorporation();
        Instant morning = now.truncatedTo(ChronoUnit.DAYS).plus(Duration.ofHours(8));
        Instant evening = morning.plus(Duration.ofHours(10));
        corporationDao.saveWallets(CORPORATION, Map.of(1, 1_000.0));

        tracker(morning).capture();
        corporationDao.saveWallets(CORPORATION, Map.of(1, 2_000.0));
        tracker(evening).capture();

        List<TrackerSnapshot> snapshots = snapshotDao.listBetween(Set.of(CORPORATION), Instant.EPOCH,
                evening.plusSeconds(1));
        assertEquals(1, snapshots.size());
        assertEquals(evening, snapshots.getFirst().capturedAt());
        assertEquals(2_000.0, snapshots.getFirst().walletBalance(), 0.01);
        assertEquals(List.of(CORPORATION), snapshotDao.corporationIds());
        assertEquals(List.of("Deep Core Mining [DCM]"),
                tracker(evening).corporations().stream().map(Corporation::label).toList());
    }

    @Test
    void aCorporationNotReadForADayIsNotCaptured() {
        corporationDao.saveWallets(CORPORATION, Map.of(1, 1_000.0));

        tracker(now.plus(Duration.ofDays(2))).capture();

        assertEquals(List.of(), snapshotDao.corporationIds());
    }

    @Test
    void aSnapshotCanBeDeleted() {
        fillCorporation();
        CorporationTracker tracker = tracker(now);
        tracker.capture();
        TrackerSnapshot saved = tracker.snapshots(Set.of(CORPORATION), Instant.EPOCH, now.plusSeconds(1)).getFirst();

        tracker.delete(saved);

        assertEquals(List.of(), tracker.snapshots(Set.of(CORPORATION), Instant.EPOCH, now.plusSeconds(1)));
    }

    private static JournalEntry journal(long id, String date, String refType, double amount) {
        return new JournalEntry(id, date, refType, amount, 0, null, null, null, null, 0, null);
    }

    @Test
    void theCorporationEarnsItsTaxesTradesAndJobs() {
        Instant at = Instant.parse("2026-11-20T12:00:00Z");
        corporationDao.saveJournal(CORPORATION, 1, List.of(
                journal(1, "2026-11-18T10:00:00Z", "bounty_prizes", 30_000_000),
                journal(2, "2026-11-18T11:00:00Z", "agent_mission_reward", 5_000_000),
                journal(3, "2026-11-19T10:00:00Z", "brokers_fee", -1_000_000),
                journal(4, "2026-11-19T11:00:00Z", "corporation_account_withdrawal", -2_000_000_000),
                journal(5, "2026-11-01T10:00:00Z", "bounty_prizes", 99_000_000)));
        corporationDao.saveTransactions(CORPORATION, 1, List.of(
                new TransactionEntry(1, "2026-11-19T10:00:00Z", 34, 10, 2_000_000, 1, 60003760L, false, false, 3),
                new TransactionEntry(2, "2026-11-19T12:00:00Z", 34, 5, 1_000_000, 1, 60003760L, true, false, 4)));
        industryDao.saveJobs(CORPORATION, List.of(new CorporationIndustryDao.Job(new IndustryJobEntry(9, 1,
                "delivered", BLUEPRINT, PRODUCT, 3, 0.0, 1L, 1L, "2026-11-15T10:00:00Z", "2026-11-19T10:00:00Z"),
                90L)));
        Instant since = Instant.parse("2026-11-10T00:00:00Z");
        CorporationTracker tracker = tracker(at);

        Map<Source, NavigableMap<LocalDate, Double>> daily = tracker.daily(
                EnumSet.of(Source.BOUNTIES, Source.MISSIONS, Source.MARKET, Source.INDUSTRY, Source.LP),
                Set.of(CORPORATION), since);

        LocalDate day18 = LocalDate.parse("2026-11-18");
        LocalDate day19 = LocalDate.parse("2026-11-19");
        assertEquals(Map.of(day18, 30_000_000.0), daily.get(Source.BOUNTIES));
        assertEquals(Map.of(day18, 5_000_000.0), daily.get(Source.MISSIONS));
        assertEquals(Map.of(day19, 20_000_000.0 - 5_000_000 - 1_000_000), daily.get(Source.MARKET));
        assertEquals(Map.of(day19, 3_000_000.0), daily.get(Source.INDUSTRY));
        assertTrue(daily.get(Source.LP).isEmpty(), "a corporation earns no LP");
        assertEquals(List.of(new Forecast.Transfer(Instant.parse("2026-11-19T11:00:00Z"), -2_000_000_000)),
                tracker.transfers(Set.of(CORPORATION), since));
    }
}
