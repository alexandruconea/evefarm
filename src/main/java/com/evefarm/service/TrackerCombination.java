package com.evefarm.service;

import com.evefarm.model.TrackerSnapshot;
import com.evefarm.service.IncomeSources.Source;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;

public record TrackerCombination(String name, List<String> parts) {

    public static final String WALLET = "Wallet Balance";
    public static final String LP = "LP Value";
    public static final String MINING = Source.MINING.label();
    public static final List<String> SNAPSHOT_PARTS = List.copyOf(TrackerSnapshot.VALUE_PARTS.keySet());
    public static final List<String> EARNED_PARTS = Arrays.stream(Source.values()).map(Source::label).toList();
    public static final List<String> ALL_PARTS = Stream.concat(SNAPSHOT_PARTS.stream(), EARNED_PARTS.stream())
            .toList();

    public TrackerCombination {
        List<String> chosen = parts == null ? List.of() : parts;
        parts = ALL_PARTS.stream().filter(chosen::contains).toList();
    }

    public boolean includes(String part) {
        return parts.contains(part);
    }

    public boolean usesSnapshots() {
        return parts.stream().anyMatch(TrackerSnapshot.VALUE_PARTS::containsKey);
    }

    public Set<Source> sources() {
        Set<Source> sources = EnumSet.noneOf(Source.class);
        parts.stream().map(Source::byLabel).flatMap(Optional::stream).forEach(sources::add);
        return sources;
    }

    public double snapshotValue(TrackerSnapshot snapshot) {
        double sum = 0;
        for (String part : parts) {
            ToDoubleFunction<TrackerSnapshot> value = TrackerSnapshot.VALUE_PARTS.get(part);
            if (value != null) {
                sum += value.applyAsDouble(snapshot);
            }
        }
        return sum;
    }

    public static List<TrackerCombination> defaults() {
        return List.of(
                new TrackerCombination("Net Worth", SNAPSHOT_PARTS),
                new TrackerCombination("Wallet + LP", List.of(WALLET, LP)),
                new TrackerCombination("Wallet + LP + Mining", List.of(WALLET, LP, MINING)),
                new TrackerCombination("Everything Earned", EARNED_PARTS));
    }
}
