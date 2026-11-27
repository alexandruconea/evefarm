package com.evefarm.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

public final class Forecast {

    public static final Set<String> TRANSFER_REF_TYPES = Set.of(
            "corporation_account_withdrawal", "corporation_account_deposit", "player_donation");
    public static final double LOW_PERCENTILE = 0.1;
    public static final double HIGH_PERCENTILE = 0.9;
    static final Duration MIN_SPAN = Duration.ofDays(3);
    static final int PATHS = 1_000;
    static final int MAX_DAYS = 731;
    private static final int MIN_POINTS = 3;
    private static final long SEED = 7_919L;
    private static final double OUTLIER_SPREADS = 5;
    private static final double MAX_GOAL_DAYS = 3_650;
    private static final double SECONDS_PER_DAY = Duration.ofDays(1).toSeconds();

    public record Point(Instant at, double total) {
    }

    public record Transfer(Instant at, double amount) {
    }

    public record Pace(double perDay, double spanDays, double[] dailyRates) {
    }

    public record Band(Instant at, double expected, double low, double high) {
    }

    public record Goal(double target, boolean reached, Instant expected, Instant earliest, Instant likely,
                       double neededPerDay) {
    }

    public record Projection(List<Point> history, Instant end, Pace pace, List<Band> band) {

        public boolean isEmpty() {
            return history.isEmpty();
        }

        public Point latest() {
            return history.getLast();
        }

        public double daysLeft() {
            return Math.max(0, days(latest().at(), end));
        }

        public double expected() {
            return pace == null ? latest().total() : latest().total() + pace.perDay() * daysLeft();
        }

        public double low() {
            return band.isEmpty() ? expected() : band.getLast().low();
        }

        public double high() {
            return band.isEmpty() ? expected() : band.getLast().high();
        }

        public Goal goal(double target) {
            Point latest = latest();
            boolean reached = latest.total() >= target;
            double needed = daysLeft() > 0 ? (target - latest.total()) / daysLeft() : 0;
            Instant expectedAt = null;
            if (!reached && pace != null && pace.perDay() > 0) {
                double days = (target - latest.total()) / pace.perDay();
                if (days <= MAX_GOAL_DAYS) {
                    expectedAt = plusDays(latest.at(), days);
                }
            }
            Instant earliest = band.stream().filter(step -> step.high() >= target).findFirst()
                    .map(Band::at).orElse(null);
            Instant likely = band.stream().filter(step -> step.low() >= target).findFirst()
                    .map(Band::at).orElse(null);
            return new Goal(target, reached, expectedAt, earliest, likely, needed);
        }
    }

    private Forecast() {
    }

    public static Projection project(List<Point> points, List<Transfer> transfers, Instant historyStart,
                                     Instant end, Duration window, ZoneId zone) {
        List<Point> daily = lastOfEachDay(points, zone);
        if (daily.isEmpty()) {
            return new Projection(List.of(), end, null, List.of());
        }
        List<Point> history = daily.stream().filter(point -> !point.at().isBefore(historyStart)).toList();
        if (history.isEmpty()) {
            history = List.of(daily.getLast());
        }
        Pace pace = pace(daily, transfers, window);
        Point latest = daily.getLast();
        List<Band> band = pace == null || pace.dailyRates().length < 2 ? List.of()
                : band(latest, end, pace);
        return new Projection(history, end, pace, band);
    }

    static List<Point> lastOfEachDay(List<Point> points, ZoneId zone) {
        Map<LocalDate, Point> byDay = new TreeMap<>();
        points.stream()
                .sorted(Comparator.comparing(Point::at))
                .forEach(point -> byDay.put(LocalDate.ofInstant(point.at(), zone), point));
        return List.copyOf(byDay.values());
    }

    static Pace pace(List<Point> daily, List<Transfer> transfers, Duration window) {
        Instant windowStart = daily.getLast().at().minus(window);
        List<Point> recent = daily.stream().filter(point -> !point.at().isBefore(windowStart)).toList();
        if (recent.size() < MIN_POINTS
                || Duration.between(recent.getFirst().at(), recent.getLast().at()).compareTo(MIN_SPAN) < 0) {
            return null;
        }
        Instant origin = recent.getFirst().at();
        double[] days = new double[recent.size()];
        double[] produced = new double[recent.size()];
        for (int i = 0; i < recent.size(); i++) {
            Point point = recent.get(i);
            days[i] = days(origin, point.at());
            produced[i] = point.total() - movedUntil(transfers, origin, point.at());
        }
        double[] rates = new double[recent.size() - 1];
        double[] lengths = new double[recent.size() - 1];
        for (int i = 1; i < recent.size(); i++) {
            lengths[i - 1] = days[i] - days[i - 1];
            rates[i - 1] = (produced[i] - produced[i - 1]) / lengths[i - 1];
        }
        return new Pace(typicalRate(rates, lengths), days[days.length - 1], rates);
    }

    static double typicalRate(double[] rates, double[] lengths) {
        double[] usual = withoutOutliers(rates);
        double sum = 0;
        double total = 0;
        for (int i = 0; i < usual.length; i++) {
            sum += usual[i] * lengths[i];
            total += lengths[i];
        }
        return total > 0 ? sum / total : 0;
    }

    static double[] withoutOutliers(double[] values) {
        double middle = median(values);
        double spread = Arrays.stream(values).map(value -> Math.abs(value - middle)).average().orElse(0)
                * OUTLIER_SPREADS;
        if (spread <= 0) {
            return values.clone();
        }
        return Arrays.stream(values).map(value -> Math.clamp(value, middle - spread, middle + spread)).toArray();
    }

    static List<Band> band(Point latest, Instant end, Pace pace) {
        double daysLeft = days(latest.at(), end);
        if (daysLeft <= 0) {
            return List.of();
        }
        int steps = (int) Math.min(MAX_DAYS, Math.max(1, Math.ceil(daysLeft)));
        double stepDays = daysLeft / steps;
        double[] rates = withoutOutliers(pace.dailyRates());
        double shift = pace.perDay() - Arrays.stream(rates).average().orElse(0);
        Random random = new Random(SEED);
        double[][] paths = new double[steps][PATHS];
        for (int path = 0; path < PATHS; path++) {
            double value = 0;
            for (int step = 0; step < steps; step++) {
                value += (rates[random.nextInt(rates.length)] + shift) * stepDays;
                paths[step][path] = value;
            }
        }
        List<Band> band = new ArrayList<>(steps + 1);
        band.add(new Band(latest.at(), latest.total(), latest.total(), latest.total()));
        for (int step = 0; step < steps; step++) {
            double[] values = paths[step];
            Arrays.sort(values);
            double elapsed = stepDays * (step + 1);
            band.add(new Band(step == steps - 1 ? end : plusDays(latest.at(), elapsed),
                    latest.total() + pace.perDay() * elapsed,
                    latest.total() + values[(int) (LOW_PERCENTILE * (PATHS - 1))],
                    latest.total() + values[(int) Math.ceil(HIGH_PERCENTILE * (PATHS - 1))]));
        }
        return band;
    }

    private static double movedUntil(List<Transfer> transfers, Instant from, Instant to) {
        return transfers.stream()
                .filter(transfer -> transfer.at().isAfter(from) && !transfer.at().isAfter(to))
                .mapToDouble(Transfer::amount)
                .sum();
    }

    private static double median(double[] values) {
        if (values.length == 0) {
            return 0;
        }
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        int middle = sorted.length / 2;
        return sorted.length % 2 == 1 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2;
    }

    static double days(Instant from, Instant to) {
        return Duration.between(from, to).toSeconds() / SECONDS_PER_DAY;
    }

    static Instant plusDays(Instant from, double days) {
        return from.plusSeconds(Math.round(days * SECONDS_PER_DAY));
    }
}
