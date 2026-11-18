package com.evefarm.service;

import com.evefarm.db.dao.SettingsDao;
import com.evefarm.db.dao.SnapshotDao;
import com.evefarm.db.dao.WalletJournalDao;
import com.evefarm.model.MiningRow;
import com.evefarm.model.TrackerSnapshot;
import com.evefarm.service.WalletLpProjection.Projection;
import com.evefarm.service.WalletLpProjection.Transfer;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.ToDoubleFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class TrackerCombinationService {

    private static final Logger LOG = Logger.getLogger(TrackerCombinationService.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record Point(Instant at, double value) {
    }

    private final SnapshotDao snapshotDao;
    private final WalletJournalDao walletJournalDao;
    private final MiningService miningService;
    private final SettingsDao settingsDao;
    private final Clock clock;

    public TrackerCombinationService(SnapshotDao snapshotDao, WalletJournalDao walletJournalDao,
                                     MiningService miningService, SettingsDao settingsDao, Clock clock) {
        this.snapshotDao = snapshotDao;
        this.walletJournalDao = walletJournalDao;
        this.miningService = miningService;
        this.settingsDao = settingsDao;
        this.clock = clock;
    }

    public List<TrackerCombination> combinations() {
        String json = settingsDao.getOrDefault(SettingsDao.TRACKER_COMBINATIONS, "");
        if (json.isBlank()) {
            return TrackerCombination.defaults();
        }
        try {
            return List.of(MAPPER.readValue(json, TrackerCombination[].class));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Couldn't read the saved Tracker combinations", e);
            return TrackerCombination.defaults();
        }
    }

    public void saveCombinations(List<TrackerCombination> combinations) {
        try {
            settingsDao.set(SettingsDao.TRACKER_COMBINATIONS, MAPPER.writeValueAsString(combinations));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Couldn't save the Tracker combinations", e);
        }
    }

    public Optional<TrackerCombination> selected() {
        String name = settingsDao.getOrDefault(SettingsDao.TRACKER_COMBINATION, "");
        return combinations().stream().filter(combination -> combination.name().equals(name)).findFirst();
    }

    public void select(String name) {
        settingsDao.set(SettingsDao.TRACKER_COMBINATION, name == null ? "" : name);
    }

    public TrackerCombination forecastCombination() {
        List<TrackerCombination> combinations = combinations();
        TrackerCombination walletAndLp = new TrackerCombination("Wallet + LP",
                List.of(TrackerCombination.WALLET, TrackerCombination.LP));
        return selected()
                .or(() -> combinations.stream().filter(c -> c.parts().equals(walletAndLp.parts())).findFirst())
                .or(() -> combinations.stream().findFirst())
                .orElse(walletAndLp);
    }

    static List<Point> points(List<TrackerSnapshot> snapshots, TrackerCombination combination,
                              ToDoubleFunction<Instant> mined) {
        Map<Long, TrackerSnapshot> latestPerCharacter = new LinkedHashMap<>();
        List<Point> points = new ArrayList<>();
        for (List<TrackerSnapshot> run : SnapshotRuns.of(snapshots)) {
            run.forEach(snapshot -> latestPerCharacter.put(snapshot.characterId(), snapshot));
            Instant at = run.getFirst().capturedAt();
            double value = latestPerCharacter.values().stream().mapToDouble(combination::snapshotValue).sum();
            points.add(new Point(at, value + mined.applyAsDouble(at)));
        }
        return points;
    }

    static ToDoubleFunction<Instant> cumulative(NavigableMap<LocalDate, Double> valueByDay) {
        NavigableMap<LocalDate, Double> runningTotal = new TreeMap<>();
        double total = 0;
        for (Map.Entry<LocalDate, Double> day : valueByDay.entrySet()) {
            total += day.getValue();
            runningTotal.put(day.getKey(), total);
        }
        return at -> {
            Map.Entry<LocalDate, Double> upTo = runningTotal.floorEntry(LocalDate.ofInstant(at, ZoneOffset.UTC));
            return upTo == null ? 0 : upTo.getValue();
        };
    }

    private ToDoubleFunction<Instant> minedUntil(Set<Long> characterIds) {
        MiningService.Valuation valuation = settingsDao.getEnum(SettingsDao.MINING_VALUATION,
                MiningService.Valuation.class).orElse(MiningService.Valuation.ORE);
        double refineRate = Math.clamp(settingsDao.getDouble(SettingsDao.MINING_REFINE_RATE,
                MiningService.DEFAULT_REFINE_RATE), 0, 100) / 100.0;
        NavigableMap<LocalDate, Double> valueByDay = new TreeMap<>();
        for (MiningRow row : miningService.getRows(MiningService.Period.ALL, valuation, refineRate)) {
            if (characterIds.contains(row.characterId()) && row.value() != null) {
                valueByDay.merge(LocalDate.parse(row.date()), row.value(), Double::sum);
            }
        }
        return cumulative(valueByDay);
    }

    public Projection project(TrackerCombination combination, Set<Long> characterIds) {
        ToDoubleFunction<Instant> mined = combination.includes(TrackerCombination.MINING)
                ? minedUntil(characterIds) : at -> 0;
        Instant now = clock.instant();
        ZoneId zone = clock.getZone();
        Instant paceStart = now.minus(WalletLpProjection.PACE_WINDOW).minus(Duration.ofDays(1));
        Instant yearStart = WalletLpProjection.yearStart(now, zone);
        Instant since = paceStart.isBefore(yearStart) ? paceStart : yearStart;
        List<WalletLpProjection.Point> points = points(snapshotDao.listBetween(characterIds, since, now),
                combination, mined).stream()
                .map(point -> new WalletLpProjection.Point(point.at(), point.value(), 0))
                .toList();
        List<Transfer> transfers = new ArrayList<>();
        if (combination.includes(TrackerCombination.WALLET)) {
            for (long characterId : characterIds) {
                walletJournalDao.findByRefTypes(characterId, WalletLpProjection.TRANSFER_REF_TYPES, since)
                        .forEach(entry -> transfers.add(new Transfer(Instant.parse(entry.date()), entry.amount())));
            }
        }
        return WalletLpProjection.project(points, transfers, now, zone);
    }
}
