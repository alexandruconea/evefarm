package com.evefarm.service;

import com.evefarm.model.AbyssTier;
import com.evefarm.model.AbyssWeather;
import com.evefarm.model.AbyssalRun;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class AbyssStats {

    public record Summary(String label, int runs, int deaths, double loot, double filamentCost, double profit,
                          Double averageSeconds, Double iskPerHour) {
        public double averageProfit() {
            return runs == 0 ? 0 : profit / runs;
        }
    }

    private AbyssStats() {
    }

    public static Summary overall(Collection<AbyssalRun> runs) {
        return summarize("All runs", runs);
    }

    public static List<Summary> byTier(Collection<AbyssalRun> runs) {
        return grouped(runs, run -> run.tier() == null ? null : run.tier().toString(),
                Comparator.comparing(AbyssalRun::tier, Comparator.nullsLast(Comparator.naturalOrder())));
    }

    public static List<Summary> byFilament(Collection<AbyssalRun> runs) {
        return grouped(runs, AbyssStats::filamentLabel,
                Comparator.comparing(AbyssalRun::tier, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(AbyssalRun::weather, Comparator.nullsLast(Comparator.naturalOrder())));
    }

    static String filamentLabel(AbyssalRun run) {
        AbyssTier tier = run.tier();
        AbyssWeather weather = run.weather();
        if (tier == null && weather == null) {
            return null;
        }
        if (tier == null) {
            return weather.toString();
        }
        return weather == null ? tier.toString() : tier + " " + weather;
    }

    private static List<Summary> grouped(Collection<AbyssalRun> runs, Function<AbyssalRun, String> label,
                                         Comparator<AbyssalRun> order) {
        Map<String, List<AbyssalRun>> groups = new LinkedHashMap<>();
        runs.stream().sorted(order).forEach(run -> {
            String key = label.apply(run);
            groups.computeIfAbsent(key == null ? "Unknown" : key, ignored -> new ArrayList<>()).add(run);
        });
        List<Summary> summaries = new ArrayList<>();
        groups.forEach((key, group) -> summaries.add(summarize(key, group)));
        return summaries;
    }

    static Summary summarize(String label, Collection<AbyssalRun> runs) {
        int deaths = 0;
        double loot = 0;
        double filamentCost = 0;
        double profit = 0;
        long timedSeconds = 0;
        int timedRuns = 0;
        double timedProfit = 0;
        for (AbyssalRun run : runs) {
            if (!run.survived()) {
                deaths++;
            }
            loot += run.lootValue();
            filamentCost += run.filamentCost() == null ? 0 : run.filamentCost();
            profit += run.profit();
            if (run.durationSeconds() != null && run.durationSeconds() > 0) {
                timedSeconds += run.durationSeconds();
                timedRuns++;
                timedProfit += run.profit();
            }
        }
        Double averageSeconds = timedRuns == 0 ? null : (double) timedSeconds / timedRuns;
        Double iskPerHour = timedSeconds == 0 ? null : timedProfit * 3600.0 / timedSeconds;
        return new Summary(label, runs.size(), deaths, loot, filamentCost, profit, averageSeconds, iskPerHour);
    }
}
