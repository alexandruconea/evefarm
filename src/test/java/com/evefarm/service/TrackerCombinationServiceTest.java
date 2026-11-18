package com.evefarm.service;

import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.db.dao.CharacterDao;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.db.dao.SnapshotDao;
import com.evefarm.db.dao.WalletJournalDao;
import com.evefarm.model.TrackerSnapshot;
import com.evefarm.service.TrackerCombinationService.Point;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrackerCombinationServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-18T12:00:00Z");
    private static final TrackerCombination WALLET_AND_LP =
            new TrackerCombination("Wallet + LP", List.of(TrackerCombination.WALLET, TrackerCombination.LP));

    private SettingsDao settingsDao;
    private SnapshotDao snapshotDao;
    private TrackerCombinationService service;

    @BeforeEach
    void setUp() {
        Database database = new Database(":memory:");
        MigrationRunner.run(database);
        settingsDao = new SettingsDao(database);
        snapshotDao = new SnapshotDao(database);
        CharacterDao characterDao = new CharacterDao(database);
        characterDao.upsert(1, "Alpha", List.of(), "a");
        characterDao.upsert(2, "Bravo", List.of(), "b");
        service = new TrackerCombinationService(snapshotDao, new WalletJournalDao(database), null, settingsDao,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static TrackerSnapshot snapshot(long characterId, Instant at, double wallet, double assets, double lp) {
        return TrackerSnapshot.of(characterId, at, wallet, assets, 0, 0, 0, 0, 0, 0, 0, 0, 0, lp);
    }

    @Test
    void aCombinationAddsOnlyItsPartsForEveryCharacterAndTheMiningSoFar() {
        Instant first = Instant.parse("2026-11-10T10:00:00Z");
        Instant second = Instant.parse("2026-11-11T10:00:00Z");
        List<TrackerSnapshot> snapshots = List.of(
                snapshot(1, first, 100, 5_000, 10),
                snapshot(2, first.plusSeconds(30), 200, 7_000, 20),
                snapshot(1, second, 150, 5_000, 10));
        ToDoubleFunction<Instant> mined = at -> at.isBefore(second) ? 1 : 3;

        List<Point> points = TrackerCombinationService.points(snapshots,
                new TrackerCombination("Farm", List.of(TrackerCombination.WALLET, TrackerCombination.LP,
                        TrackerCombination.MINING)), mined);

        assertEquals(List.of(new Point(first, 100 + 10 + 200 + 20 + 1), new Point(second, 150 + 10 + 200 + 20 + 3)),
                points);
    }

    @Test
    void minedValueAddsUpDayByDay() {
        TreeMap<LocalDate, Double> byDay = new TreeMap<>();
        byDay.put(LocalDate.parse("2026-11-02"), 5.0);
        byDay.put(LocalDate.parse("2026-11-05"), 7.0);

        ToDoubleFunction<Instant> mined = TrackerCombinationService.cumulative(byDay);

        assertEquals(0, mined.applyAsDouble(Instant.parse("2026-11-01T23:00:00Z")));
        assertEquals(5, mined.applyAsDouble(Instant.parse("2026-11-04T08:00:00Z")));
        assertEquals(12, mined.applyAsDouble(Instant.parse("2026-11-05T00:30:00Z")));
        assertEquals(12, mined.applyAsDouble(Instant.parse("2026-12-01T00:00:00Z")));
    }

    @Test
    void savedCombinationsAndTheChosenOneAreKept() {
        assertEquals(TrackerCombination.defaults(), service.combinations());
        assertTrue(service.selected().isEmpty());

        TrackerCombination liquid = new TrackerCombination("Liquid",
                List.of("Sell Orders", TrackerCombination.WALLET, "Not a part"));
        service.saveCombinations(List.of(liquid, WALLET_AND_LP));
        service.select("Liquid");

        assertEquals(List.of(TrackerCombination.WALLET, "Sell Orders"), service.combinations().getFirst().parts());
        assertEquals(List.of(liquid, WALLET_AND_LP), service.combinations());
        assertEquals("Liquid", service.selected().orElseThrow().name());
        service.select(null);
        assertTrue(service.selected().isEmpty());
    }

    @Test
    void theForecastFollowsTheTrendOfTheCombination() {
        for (int day = 20; day >= 0; day--) {
            Instant at = NOW.minus(Duration.ofDays(day));
            snapshotDao.insert(snapshot(1, at, 1_000_000_000L * (20 - day), 50_000_000_000L, 0));
            snapshotDao.insert(snapshot(2, at.minusSeconds(20), 0, 0, 500_000_000L * (20 - day)));
        }

        WalletLpProjection.Projection projection = service.project(WALLET_AND_LP, Set.of(1L, 2L));

        assertEquals(30_000_000_000.0, projection.latest().total(), 1);
        assertNotNull(projection.pace());
        assertEquals(1_500_000_000.0, projection.pace().perDay(), 1_000);
        TrackerCombination walletOnly = new TrackerCombination("Wallet", List.of(TrackerCombination.WALLET));
        assertEquals(1_000_000_000.0, service.project(walletOnly, Set.of(1L)).pace().perDay(), 1_000);
    }

    @Test
    void withNothingChosenTheForecastAddsUpWalletAndLp() {
        assertEquals("Wallet + LP", service.forecastCombination().name());

        TrackerCombination liquid = new TrackerCombination("Liquid", List.of(TrackerCombination.WALLET, "Sell Orders"));
        TrackerCombination farm = new TrackerCombination("Farm",
                List.of(TrackerCombination.LP, TrackerCombination.MINING));
        service.saveCombinations(List.of(liquid, farm));
        assertEquals(liquid, service.forecastCombination(), "without Wallet + LP the first one is used");

        service.select("Farm");
        assertEquals(farm, service.forecastCombination());

        service.saveCombinations(List.of());
        assertEquals(WALLET_AND_LP.parts(), service.forecastCombination().parts());
    }
}
