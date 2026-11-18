package com.evefarm.service;

import com.evefarm.model.TrackerSnapshot;

import java.util.List;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;

public record TrackerCombination(String name, List<String> parts) {

    public static final String WALLET = "Wallet Balance";
    public static final String LP = "LP Value";
    public static final String MINING = "Mining";
    public static final List<String> ALL_PARTS =
            Stream.concat(TrackerSnapshot.VALUE_PARTS.keySet().stream(), Stream.of(MINING)).toList();

    public TrackerCombination {
        List<String> chosen = parts == null ? List.of() : parts;
        parts = ALL_PARTS.stream().filter(chosen::contains).toList();
    }

    public boolean includes(String part) {
        return parts.contains(part);
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
                new TrackerCombination("Net Worth", List.copyOf(TrackerSnapshot.VALUE_PARTS.keySet())),
                new TrackerCombination("Wallet + LP", List.of(WALLET, LP)),
                new TrackerCombination("Wallet + LP + Mining", List.of(WALLET, LP, MINING)));
    }
}
