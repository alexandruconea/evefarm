package com.evefarm.ui;

import com.evefarm.model.TrackerSnapshot;
import org.jfree.data.time.Minute;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import java.awt.Color;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.ToDoubleFunction;

import static java.util.Map.entry;

public final class TrackerChartFactory {

    public static final List<String> SERIES_NAMES = List.of(
            "Total", "Wallet Balance", "Assets", "Implants", "Sell Orders",
            "Escrows", "Escrows To Cover", "Manufacturing", "Contract Collateral",
            "Contracts", "Skill Points", "LP Value"
    );

    private static final Map<String, ToDoubleFunction<TrackerSnapshot>> TOTAL_COMPONENTS = new LinkedHashMap<>();

    static {
        TOTAL_COMPONENTS.put("Wallet Balance", TrackerSnapshot::walletBalance);
        TOTAL_COMPONENTS.put("Assets", TrackerSnapshot::assetsValue);
        TOTAL_COMPONENTS.put("Implants", TrackerSnapshot::implantsValue);
        TOTAL_COMPONENTS.put("Sell Orders", TrackerSnapshot::sellOrdersValue);
        TOTAL_COMPONENTS.put("Escrows", TrackerSnapshot::escrowValue);
        TOTAL_COMPONENTS.put("Manufacturing", TrackerSnapshot::manufacturingValue);
        TOTAL_COMPONENTS.put("Contract Collateral", TrackerSnapshot::contractCollateralValue);
        TOTAL_COMPONENTS.put("Contracts", TrackerSnapshot::contractsValue);
        TOTAL_COMPONENTS.put("Skill Points", TrackerSnapshot::skillPointValue);
        TOTAL_COMPONENTS.put("LP Value", TrackerSnapshot::lpValue);
    }

    private static final Map<String, Color> SERIES_COLORS_LIGHT = Map.ofEntries(
            entry("Total", new Color(0x2a, 0x78, 0xd6)),
            entry("Wallet Balance", new Color(0xeb, 0x68, 0x34)),
            entry("Assets", new Color(0x1b, 0xaf, 0x7a)),
            entry("Implants", new Color(0x1b, 0xaf, 0x7a)),
            entry("Sell Orders", new Color(0xed, 0xa1, 0x00)),
            entry("Escrows", new Color(0xe8, 0x7b, 0xa4)),
            entry("Escrows To Cover", new Color(0x89, 0x87, 0x81)),
            entry("Manufacturing", new Color(0x00, 0x83, 0x00)),
            entry("Contract Collateral", new Color(0x4a, 0x3a, 0xa7)),
            entry("Contracts", new Color(0x4a, 0x3a, 0xa7)),
            entry("Skill Points", new Color(0xed, 0xa1, 0x00)),
            entry("LP Value", new Color(0xe3, 0x49, 0x48))
    );

    private static final Map<String, Color> SERIES_COLORS_DARK = Map.ofEntries(
            entry("Total", new Color(0x39, 0x87, 0xe5)),
            entry("Wallet Balance", new Color(0xd9, 0x59, 0x26)),
            entry("Assets", new Color(0x19, 0x9e, 0x70)),
            entry("Implants", new Color(0x19, 0x9e, 0x70)),
            entry("Sell Orders", new Color(0xc9, 0x85, 0x00)),
            entry("Escrows", new Color(0xd5, 0x51, 0x81)),
            entry("Escrows To Cover", new Color(0x89, 0x87, 0x81)),
            entry("Manufacturing", new Color(0x00, 0x83, 0x00)),
            entry("Contract Collateral", new Color(0x90, 0x85, 0xe9)),
            entry("Contracts", new Color(0x90, 0x85, 0xe9)),
            entry("Skill Points", new Color(0xc9, 0x85, 0x00)),
            entry("LP Value", new Color(0xe6, 0x67, 0x67))
    );

    private static final Set<String> SERIES_DASHED =
            Set.of("Implants", "Escrows To Cover", "Contract Collateral", "Skill Points");

    public static Color colorFor(String seriesName, boolean dark) {
        Map<String, Color> palette = dark ? SERIES_COLORS_DARK : SERIES_COLORS_LIGHT;
        return palette.getOrDefault(seriesName, dark ? Color.LIGHT_GRAY : Color.DARK_GRAY);
    }

    public static boolean isDashed(String seriesName) {
        return SERIES_DASHED.contains(seriesName);
    }

    public TimeSeriesCollection buildDataset(List<TrackerSnapshot> snapshots, Set<String> visibleSeriesNames) {
        Map<Instant, Map<Long, TrackerSnapshot>> latestPerCharacterPerMinute = new TreeMap<>();
        for (TrackerSnapshot snapshot : snapshots) {
            Instant minute = snapshot.capturedAt().truncatedTo(ChronoUnit.MINUTES);
            Map<Long, TrackerSnapshot> perCharacter =
                    latestPerCharacterPerMinute.computeIfAbsent(minute, m -> new LinkedHashMap<>());
            TrackerSnapshot existing = perCharacter.get(snapshot.characterId());
            if (existing == null || snapshot.capturedAt().isAfter(existing.capturedAt())) {
                perCharacter.put(snapshot.characterId(), snapshot);
            }
        }

        Map<String, TimeSeries> series = new LinkedHashMap<>();
        for (String name : SERIES_NAMES) {
            if (visibleSeriesNames.contains(name)) {
                series.put(name, new TimeSeries(name));
            }
        }

        for (Map.Entry<Instant, Map<Long, TrackerSnapshot>> minuteEntry : latestPerCharacterPerMinute.entrySet()) {
            Minute minute = new Minute(Date.from(minuteEntry.getKey()));
            List<TrackerSnapshot> pointSnapshots = new ArrayList<>(minuteEntry.getValue().values());

            addIfVisible(series, "Wallet Balance", minute, pointSnapshots, TrackerSnapshot::walletBalance);
            addIfVisible(series, "Assets", minute, pointSnapshots, TrackerSnapshot::assetsValue);
            addIfVisible(series, "Implants", minute, pointSnapshots, TrackerSnapshot::implantsValue);
            addIfVisible(series, "Sell Orders", minute, pointSnapshots, TrackerSnapshot::sellOrdersValue);
            addIfVisible(series, "Escrows", minute, pointSnapshots, TrackerSnapshot::escrowValue);
            addIfVisible(series, "Escrows To Cover", minute, pointSnapshots, TrackerSnapshot::escrowToCoverValue);
            addIfVisible(series, "Manufacturing", minute, pointSnapshots, TrackerSnapshot::manufacturingValue);
            addIfVisible(series, "Contract Collateral", minute, pointSnapshots, TrackerSnapshot::contractCollateralValue);
            addIfVisible(series, "Contracts", minute, pointSnapshots, TrackerSnapshot::contractsValue);
            addIfVisible(series, "Skill Points", minute, pointSnapshots, TrackerSnapshot::skillPointValue);
            addIfVisible(series, "LP Value", minute, pointSnapshots, TrackerSnapshot::lpValue);

            TimeSeries totalSeries = series.get("Total");
            if (totalSeries != null) {
                double sum = pointSnapshots.stream().mapToDouble(snapshot ->
                        TOTAL_COMPONENTS.entrySet().stream()
                                .filter(e -> visibleSeriesNames.contains(e.getKey()))
                                .mapToDouble(e -> e.getValue().applyAsDouble(snapshot))
                                .sum()
                ).sum();
                totalSeries.addOrUpdate(minute, sum);
            }
        }

        TimeSeriesCollection dataset = new TimeSeriesCollection();
        for (String name : SERIES_NAMES) {
            TimeSeries ts = series.get(name);
            if (ts != null) {
                dataset.addSeries(ts);
            }
        }
        return dataset;
    }

    private void addIfVisible(Map<String, TimeSeries> series, String name, Minute minute,
                               List<TrackerSnapshot> pointSnapshots,
                               ToDoubleFunction<TrackerSnapshot> extractor) {
        TimeSeries ts = series.get(name);
        if (ts == null) {
            return;
        }
        double sum = pointSnapshots.stream().mapToDouble(extractor).sum();
        ts.addOrUpdate(minute, sum);
    }
}
