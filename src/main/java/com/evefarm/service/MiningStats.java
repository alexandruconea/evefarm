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
        DAY("Day", MiningRow::date),
        CHARACTER("Character", MiningRow::characterName),
        ORE("Ore", MiningRow::oreName),
        KIND("Kind", MiningRow::kind),
        SYSTEM("System", MiningRow::systemName);

        private final String label;
        private final Function<MiningRow, String> key;

        GroupBy(String label, Function<MiningRow, String> key) {
            this.label = label;
            this.key = key;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public record Summary(String label, double volume, double value, int days) {
        public double valuePerDay() {
            return days == 0 ? 0 : value / days;
        }
    }

    private MiningStats() {
    }

    public static Summary overall(Collection<MiningRow> rows) {
        return summarize("All mining", rows);
    }

    public static List<Summary> grouped(Collection<MiningRow> rows, GroupBy groupBy) {
        Map<String, List<MiningRow>> groups = new LinkedHashMap<>();
        for (MiningRow row : rows) {
            String key = groupBy.key.apply(row);
            groups.computeIfAbsent(key == null ? "Unknown" : key, ignored -> new ArrayList<>()).add(row);
        }
        List<Summary> summaries = new ArrayList<>();
        groups.forEach((label, group) -> summaries.add(summarize(label, group)));
        Comparator<Summary> order = groupBy == GroupBy.DAY
                ? Comparator.comparing(Summary::label).reversed()
                : Comparator.comparingDouble(Summary::value).reversed().thenComparing(Summary::label);
        summaries.sort(order);
        return summaries;
    }

    static Summary summarize(String label, Collection<MiningRow> rows) {
        double volume = 0;
        double value = 0;
        for (MiningRow row : rows) {
            volume += row.volume();
            value += row.value() == null ? 0 : row.value();
        }
        int days = (int) rows.stream().map(MiningRow::date).distinct().count();
        return new Summary(label, volume, value, days);
    }
}
