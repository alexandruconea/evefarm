package com.evefarm.service;

import com.evefarm.model.MiningRow;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class MiningStats {

    public enum GroupBy {
        DAY("Day", false, row -> whole(row, row.date())),
        CHARACTER("Character", false, row -> whole(row, row.characterName())),
        ORE("Ore", true, row -> whole(row, row.oreName())),
        KIND("Kind", false, row -> whole(row, row.kind())),
        SYSTEM("System", false, row -> whole(row, row.systemName())),
        MATERIAL("Material", true, MiningStats::refined);

        private final String label;
        private final boolean countsUnits;
        private final Function<MiningRow, List<Part>> parts;

        GroupBy(String label, boolean countsUnits, Function<MiningRow, List<Part>> parts) {
            this.label = label;
            this.countsUnits = countsUnits;
            this.parts = parts;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    // The quantity is null where a group adds up units of different items.
    public record Summary(String label, Double quantity, double volume, double value, int days) {
        public double valuePerDay() {
            return days == 0 ? 0 : value / days;
        }
    }

    private record Part(String label, double quantity, double volume, Double value, String date) {
    }

    private MiningStats() {
    }

    public static Summary overall(Collection<MiningRow> rows) {
        return summarize("All mining", rows.stream().flatMap(row -> whole(row, null).stream()).toList(), false);
    }

    public static List<Summary> grouped(Collection<MiningRow> rows, GroupBy groupBy) {
        Map<String, List<Part>> groups = new LinkedHashMap<>();
        for (MiningRow row : rows) {
            for (Part part : groupBy.parts.apply(row)) {
                String key = part.label() == null ? "Unknown" : part.label();
                groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(part);
            }
        }
        List<Summary> summaries = new ArrayList<>();
        groups.forEach((label, group) -> summaries.add(summarize(label, group, groupBy.countsUnits)));
        Comparator<Summary> order = groupBy == GroupBy.DAY
                ? Comparator.comparing(Summary::label).reversed()
                : Comparator.comparingDouble(Summary::value).reversed().thenComparing(Summary::label);
        summaries.sort(order);
        return summaries;
    }

    private static List<Part> whole(MiningRow row, String label) {
        return List.of(new Part(label, row.quantity(), row.volume(), row.value(), row.date()));
    }

    // What isn't refined (gas, or an ore whose minerals have no price) stays itself, so the groups still add up.
    private static List<Part> refined(MiningRow row) {
        if (row.materials().isEmpty()) {
            return whole(row, row.oreName());
        }
        return row.materials().stream()
                .map(material -> new Part(material.name(), material.quantity(), material.volume(), material.value(),
                        row.date()))
                .toList();
    }

    private static Summary summarize(String label, Collection<Part> parts, boolean countsUnits) {
        double quantity = 0;
        double volume = 0;
        double value = 0;
        for (Part part : parts) {
            quantity += part.quantity();
            volume += part.volume();
            value += part.value() == null ? 0 : part.value();
        }
        int days = (int) parts.stream().map(Part::date).distinct().count();
        return new Summary(label, countsUnits ? quantity : null, volume, value, days);
    }
}
