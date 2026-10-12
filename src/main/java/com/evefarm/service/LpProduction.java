package com.evefarm.service;

import com.evefarm.model.LpGain;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class LpProduction {

    static final Duration UNSEEN_GAP = Duration.ofHours(3);
    public static final int LEVELS = 4;

    public record Day(LocalDate date, long points, Instant earnedSince, List<LpGain> gains) {

        public boolean includesEarlierDays() {
            return earnedSince != null;
        }
    }

    public static Map<LocalDate, Day> byDay(List<LpGain> gains, ZoneId zone) {
        Map<LocalDate, List<LpGain>> grouped = new TreeMap<>();
        for (LpGain gain : gains) {
            grouped.computeIfAbsent(gain.seenAt().atZone(zone).toLocalDate(), date -> new ArrayList<>()).add(gain);
        }
        Map<LocalDate, Day> days = new TreeMap<>();
        grouped.forEach((date, dayGains) -> days.put(date, new Day(date,
                dayGains.stream().mapToLong(LpGain::points).sum(),
                dayGains.stream()
                        .filter(gain -> spansEarlierDays(gain, zone))
                        .map(LpGain::since)
                        .min(Comparator.naturalOrder())
                        .orElse(null),
                List.copyOf(dayGains))));
        return days;
    }

    static boolean spansEarlierDays(LpGain gain, ZoneId zone) {
        return gain.since() != null
                && Duration.between(gain.since(), gain.seenAt()).compareTo(UNSEEN_GAP) > 0
                && gain.since().atZone(zone).toLocalDate().isBefore(gain.seenAt().atZone(zone).toLocalDate());
    }

    public static long total(Map<LocalDate, Day> days, LocalDate from, LocalDate to) {
        return inRange(days, from, to).stream().mapToLong(Day::points).sum();
    }

    public static Optional<Day> best(Map<LocalDate, Day> days, LocalDate from, LocalDate to) {
        return inRange(days, from, to).stream()
                .max(Comparator.comparingLong(Day::points).thenComparing(Day::date));
    }

    private static List<Day> inRange(Map<LocalDate, Day> days, LocalDate from, LocalDate to) {
        return days.values().stream()
                .filter(day -> !day.date().isBefore(from) && !day.date().isAfter(to) && day.points() > 0)
                .toList();
    }

    public record Scale(List<Long> thresholds, long max) {

        public int level(long points) {
            if (points <= 0) {
                return 0;
            }
            if (points >= max) {
                return LEVELS;
            }
            int level = 1;
            for (long threshold : thresholds) {
                if (points > threshold) {
                    level++;
                }
            }
            return Math.min(level, LEVELS);
        }
    }

    public static Scale scale(Collection<Long> points) {
        long[] sorted = points.stream().filter(value -> value > 0).mapToLong(Long::longValue).sorted().toArray();
        if (sorted.length == 0) {
            return new Scale(List.of(), 0);
        }
        List<Long> thresholds = new ArrayList<>();
        for (int quarter = 1; quarter < LEVELS; quarter++) {
            thresholds.add(sorted[(int) Math.ceil(quarter * sorted.length / (double) LEVELS) - 1]);
        }
        return new Scale(List.copyOf(thresholds), sorted[sorted.length - 1]);
    }

    private LpProduction() {
    }
}
