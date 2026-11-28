package com.evefarm.service;

import com.evefarm.db.dao.SettingsDao;
import com.evefarm.db.dao.SnapshotDao;
import com.evefarm.db.dao.WalletJournalDao;
import com.evefarm.model.TrackerSnapshot;
import com.evefarm.service.Forecast.Projection;
import com.evefarm.service.Forecast.Transfer;
import com.evefarm.service.IncomeSources.Source;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
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
    private static final Duration SHORTEST_HISTORY = Duration.ofDays(30);

    public record Point(Instant at, double value) {
    }

    private final SnapshotDao snapshotDao;
    private final WalletJournalDao walletJournalDao;
    private final IncomeSources incomeSources;
    private final SettingsDao settingsDao;
    private final CorporationTracker corporationTracker;
    private final Clock clock;

    public TrackerCombinationService(SnapshotDao snapshotDao, WalletJournalDao walletJournalDao,
                                     IncomeSources incomeSources, SettingsDao settingsDao,
                                     CorporationTracker corporationTracker, Clock clock) {
        this.snapshotDao = snapshotDao;
        this.walletJournalDao = walletJournalDao;
        this.incomeSources = incomeSources;
        this.settingsDao = settingsDao;
        this.corporationTracker = corporationTracker;
        this.clock = clock;
    }

    public Clock clock() {
        return clock;
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
        return find(name);
    }

    public Optional<TrackerCombination> find(String name) {
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
                              ToDoubleFunction<Instant> earned) {
        Map<Long, TrackerSnapshot> latestPerCharacter = new LinkedHashMap<>();
        List<Point> points = new ArrayList<>();
        for (List<TrackerSnapshot> run : SnapshotRuns.of(snapshots)) {
            run.forEach(snapshot -> latestPerCharacter.put(snapshot.characterId(), snapshot));
            Instant at = run.getFirst().capturedAt();
            double value = latestPerCharacter.values().stream().mapToDouble(combination::snapshotValue).sum();
            points.add(new Point(at, value + earned.applyAsDouble(at)));
        }
        return points;
    }

    static List<Point> dailyPoints(Instant since, Instant now, ZoneId zone, ToDoubleFunction<Instant> earned) {
        List<Point> points = new ArrayList<>();
        for (LocalDate day = LocalDate.ofInstant(since, zone); day.isBefore(LocalDate.ofInstant(now, zone));
             day = day.plusDays(1)) {
            Instant endOfDay = day.plusDays(1).atStartOfDay(zone).toInstant().minusSeconds(1);
            points.add(new Point(endOfDay, earned.applyAsDouble(endOfDay)));
        }
        points.add(new Point(now, earned.applyAsDouble(now)));
        return points;
    }

    static ToDoubleFunction<Instant> cumulative(NavigableMap<LocalDate, Double> valueByDay, ZoneId zone) {
        NavigableMap<LocalDate, Double> runningTotal = new TreeMap<>();
        double total = 0;
        for (Map.Entry<LocalDate, Double> day : valueByDay.entrySet()) {
            total += day.getValue();
            runningTotal.put(day.getKey(), total);
        }
        return at -> {
            Map.Entry<LocalDate, Double> upTo = runningTotal.floorEntry(LocalDate.ofInstant(at, zone));
            return upTo == null ? 0 : upTo.getValue();
        };
    }

    static Instant historyStart(Instant now, Instant end, Duration window) {
        Duration ahead = Duration.between(now, end);
        Duration shown = ahead.compareTo(window) > 0 ? ahead : window;
        return now.minus(shown.compareTo(SHORTEST_HISTORY) > 0 ? shown : SHORTEST_HISTORY);
    }

    public Projection project(TrackerCombination combination, Set<Long> characterIds, Instant end,
                              Duration window) {
        return project(combination, characterIds, Set.of(), end, window);
    }

    public Projection project(TrackerCombination combination, Set<Long> characterIds, Set<Long> corporationIds,
                              Instant end, Duration window) {
        Instant now = clock.instant();
        ZoneId zone = clock.getZone();
        Instant historyStart = historyStart(now, end, window);
        Instant windowStart = now.minus(window);
        Instant since = (windowStart.isBefore(historyStart) ? windowStart : historyStart).minus(Duration.ofDays(1));
        NavigableMap<LocalDate, Double> earnedByDay = new TreeMap<>();
        daily(combination.sources(), characterIds, corporationIds, since).values()
                .forEach(days -> days.forEach((day, value) -> earnedByDay.merge(day, value, Double::sum)));
        ToDoubleFunction<Instant> earned = cumulative(earnedByDay, zone);
        List<Point> points;
        if (combination.usesSnapshots()) {
            List<TrackerSnapshot> snapshots = new ArrayList<>(snapshotDao.listBetween(characterIds, since, now));
            if (!corporationIds.isEmpty()) {
                snapshots.addAll(corporationTracker.snapshots(corporationIds, since, now));
            }
            points = points(snapshots, combination, earned);
        } else {
            points = dailyPoints(since, now, zone, earned);
        }
        List<Transfer> transfers = new ArrayList<>();
        if (combination.includes(TrackerCombination.WALLET)) {
            for (long characterId : characterIds) {
                walletJournalDao.findByRefTypes(characterId, Forecast.TRANSFER_REF_TYPES, since)
                        .forEach(entry -> transfers.add(new Transfer(Instant.parse(entry.date()), entry.amount())));
            }
            if (!corporationIds.isEmpty()) {
                transfers.addAll(corporationTracker.transfers(corporationIds, since));
            }
        }
        return Forecast.project(points.stream().map(point -> new Forecast.Point(point.at(), point.value()))
                .toList(), transfers, historyStart, end, window, zone);
    }

    public Map<Source, Double> earnedPerDay(Set<Source> sources, Set<Long> characterIds, Duration window) {
        return earnedPerDay(sources, characterIds, Set.of(), window);
    }

    public Map<Source, Double> earnedPerDay(Set<Source> sources, Set<Long> characterIds, Set<Long> corporationIds,
                                            Duration window) {
        Instant since = clock.instant().minus(window);
        double days = Math.max(1, window.toSeconds() / (double) Duration.ofDays(1).toSeconds());
        Map<Source, Double> perDay = new EnumMap<>(Source.class);
        daily(sources, characterIds, corporationIds, since).forEach((source, byDay) ->
                perDay.put(source, byDay.values().stream().mapToDouble(Double::doubleValue).sum() / days));
        return perDay;
    }

    private Map<Source, NavigableMap<LocalDate, Double>> daily(Set<Source> sources, Set<Long> characterIds,
                                                               Set<Long> corporationIds, Instant since) {
        Map<Source, NavigableMap<LocalDate, Double>> bySource = incomeSources.daily(sources, characterIds, since);
        if (!corporationIds.isEmpty()) {
            corporationTracker.daily(sources, corporationIds, since).forEach((source, days) -> {
                NavigableMap<LocalDate, Double> merged = bySource.computeIfAbsent(source, key -> new TreeMap<>());
                days.forEach((day, value) -> merged.merge(day, value, Double::sum));
            });
        }
        return bySource;
    }
}
