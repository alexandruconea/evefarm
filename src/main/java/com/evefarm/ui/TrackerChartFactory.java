package com.evefarm.ui;

import com.evefarm.model.TrackerSnapshot;
import com.evefarm.service.SnapshotRuns;
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
import java.util.function.ToDoubleFunction;

import static java.util.Map.entry;

public final class TrackerChartFactory {

    public static final List<String> SERIES_NAMES = List.of(
            "Total", "Wallet Balance", "Assets", "Implants", "Sell Orders",
            "Escrows", "Escrows To Cover", "Manufacturing", "Contract Collateral",
            "Contracts", "Skill Points", "LP Value"
    );


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

    public static List<TrackerSnapshot> snapshotsAtPoint(List<TrackerSnapshot> snapshots, Instant point) {
        Instant minute = point.truncatedTo(ChronoUnit.MINUTES);
        return SnapshotRuns.of(snapshots).stream()
                .filter(run -> run.getFirst().capturedAt().truncatedTo(ChronoUnit.MINUTES).equals(minute))
                .findFirst()
                .orElse(List.of());
    }

    public TimeSeriesCollection buildDataset(List<TrackerSnapshot> snapshots, Set<String> visibleSeriesNames) {
        Map<String, TimeSeries> series = new LinkedHashMap<>();
        for (String name : SERIES_NAMES) {
            if (visibleSeriesNames.contains(name)) {
                series.put(name, new TimeSeries(name));
            }
        }

        Map<Long, TrackerSnapshot> latestPerCharacter = new LinkedHashMap<>();
        for (List<TrackerSnapshot> run : SnapshotRuns.of(snapshots)) {
            for (TrackerSnapshot snapshot : run) {
                latestPerCharacter.put(snapshot.characterId(), snapshot);
            }
            Minute minute = new Minute(Date.from(run.getFirst().capturedAt()));
            List<TrackerSnapshot> pointSnapshots = new ArrayList<>(latestPerCharacter.values());

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
                        TrackerSnapshot.VALUE_PARTS.entrySet().stream()
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
