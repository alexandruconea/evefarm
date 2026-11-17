package com.evefarm.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class WalletLpProjection {

    public static final Set<String> TRANSFER_REF_TYPES = Set.of(
            "corporation_account_withdrawal", "corporation_account_deposit", "player_donation");
    public static final Duration PACE_WINDOW = Duration.ofDays(30);
    static final Duration MIN_PACE_SPAN = Duration.ofDays(3);
    static final Duration WEEK = Duration.ofDays(7);
    static final Duration LONGEST_WEEK = Duration.ofDays(10);
    private static final double SECONDS_PER_DAY = Duration.ofDays(1).toSeconds();

    public record Point(Instant at, double wallet, double lpValue) {

        public double total() {
            return wallet + lpValue;
        }
    }

    public record Transfer(Instant at, double amount) {
    }

    public record Weeks(double slowest, double fastest) {
    }

    public record Pace(double perDay, Weeks weeks) {
    }

    public record Projection(List<Point> history, Instant end, Pace pace) {

        public boolean isEmpty() {
            return history.isEmpty();
        }

        public Point latest() {
            return history.getLast();
        }

        public double daysLeft() {
            return Math.max(0, Duration.between(latest().at(), end).toSeconds() / SECONDS_PER_DAY);
        }

        public double expected() {
            return pace == null ? latest().total() : at(pace.perDay());
        }

        public double low() {
            return pace == null || pace.weeks() == null ? expected()
                    : at(Math.min(pace.perDay(), pace.weeks().slowest()));
        }

        public double high() {
            return pace == null || pace.weeks() == null ? expected()
                    : at(Math.max(pace.perDay(), pace.weeks().fastest()));
        }

        private double at(double perDay) {
            return latest().total() + perDay * daysLeft();
        }
    }

    private WalletLpProjection() {
    }

    public static Instant yearStart(Instant now, ZoneId zone) {
        return LocalDate.ofInstant(now, zone).withDayOfYear(1).atStartOfDay(zone).toInstant();
    }

    public static Projection project(List<Point> points, List<Transfer> transfers, Instant now, ZoneId zone) {
        Instant end = LocalDate.ofInstant(now, zone).withDayOfYear(1).plusYears(1).atStartOfDay(zone).toInstant();
        List<Point> daily = lastOfEachDay(points, zone);
        if (daily.isEmpty()) {
            return new Projection(List.of(), end, null);
        }
        Instant yearStart = yearStart(now, zone);
        List<Point> history = daily.stream().filter(point -> !point.at().isBefore(yearStart)).toList();
        if (history.isEmpty()) {
            history = List.of(daily.getLast());
        }
        return new Projection(history, end, pace(daily, transfers));
    }

    static List<Point> lastOfEachDay(List<Point> points, ZoneId zone) {
        Map<LocalDate, Point> byDay = new TreeMap<>();
        points.stream()
                .sorted(Comparator.comparing(Point::at))
                .forEach(point -> byDay.put(LocalDate.ofInstant(point.at(), zone), point));
        return List.copyOf(byDay.values());
    }

    private static Pace pace(List<Point> daily, List<Transfer> transfers) {
        Instant windowStart = daily.getLast().at().minus(PACE_WINDOW);
        List<Point> window = daily.stream().filter(point -> !point.at().isBefore(windowStart)).toList();
        if (window.size() < 2
                || Duration.between(window.getFirst().at(), window.getLast().at()).compareTo(MIN_PACE_SPAN) < 0) {
            return null;
        }
        Instant origin = window.getFirst().at();
        double[] days = new double[window.size()];
        double[] produced = new double[window.size()];
        for (int i = 0; i < window.size(); i++) {
            Point point = window.get(i);
            days[i] = days(origin, point.at());
            produced[i] = point.total() - movedUntil(transfers, origin, point.at());
        }
        return new Pace(slope(days, produced), weeks(window, days, produced));
    }

    private static double movedUntil(List<Transfer> transfers, Instant from, Instant to) {
        return transfers.stream()
                .filter(transfer -> transfer.at().isAfter(from) && !transfer.at().isAfter(to))
                .mapToDouble(Transfer::amount)
                .sum();
    }

    private static double slope(double[] xs, double[] ys) {
        double meanX = 0;
        double meanY = 0;
        for (int i = 0; i < xs.length; i++) {
            meanX += xs[i] / xs.length;
            meanY += ys[i] / ys.length;
        }
        double covariance = 0;
        double variance = 0;
        for (int i = 0; i < xs.length; i++) {
            covariance += (xs[i] - meanX) * (ys[i] - meanY);
            variance += (xs[i] - meanX) * (xs[i] - meanX);
        }
        return covariance / variance;
    }

    private static Weeks weeks(List<Point> window, double[] days, double[] produced) {
        List<Double> rates = new ArrayList<>();
        for (int i = 0; i < window.size(); i++) {
            Instant weekLater = window.get(i).at().plus(WEEK);
            for (int j = i + 1; j < window.size(); j++) {
                if (window.get(j).at().isBefore(weekLater)) {
                    continue;
                }
                if (!window.get(j).at().isAfter(window.get(i).at().plus(LONGEST_WEEK))) {
                    rates.add((produced[j] - produced[i]) / (days[j] - days[i]));
                }
                break;
            }
        }
        if (rates.isEmpty()) {
            return null;
        }
        return new Weeks(rates.stream().min(Double::compare).orElseThrow(),
                rates.stream().max(Double::compare).orElseThrow());
    }

    private static double days(Instant from, Instant to) {
        return Duration.between(from, to).toSeconds() / SECONDS_PER_DAY;
    }
}
