package com.evefarm.service;

import com.evefarm.service.Forecast.Band;
import com.evefarm.service.Forecast.Goal;
import com.evefarm.service.Forecast.Point;
import com.evefarm.service.Forecast.Projection;
import com.evefarm.service.Forecast.Transfer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForecastTest {

    private static final ZoneId BUCHAREST = ZoneId.of("Europe/Bucharest");
    private static final Instant NOW = Instant.parse("2026-11-17T18:00:00Z");
    private static final Instant END = Instant.parse("2026-12-31T22:00:00Z");
    private static final Duration WINDOW = Duration.ofDays(30);
    private static final double BILLION = 1_000_000_000;
    private static final double DELTA = 1;

    private static List<Point> growing(int days, double perDay) {
        List<Point> points = new ArrayList<>();
        for (int day = days - 1; day >= 0; day--) {
            points.add(new Point(NOW.minus(Duration.ofDays(day)), 10 * BILLION + perDay * (days - 1 - day)));
        }
        return points;
    }

    private static Projection project(List<Point> points, List<Transfer> transfers) {
        return Forecast.project(points, transfers, NOW.minus(WINDOW), END, WINDOW, BUCHAREST);
    }

    private static double daysLeft() {
        return Duration.between(NOW, END).toSeconds() / 86_400.0;
    }

    @Test
    void aSteadyPaceGoesOnUntilTheEndAndHasNoSpread() {
        Projection projection = project(growing(20, BILLION), List.of());

        assertEquals(BILLION, projection.pace().perDay(), DELTA);
        assertEquals(29 * BILLION, projection.latest().total(), DELTA);
        assertEquals(29 * BILLION + daysLeft() * BILLION, projection.expected(), DELTA);
        assertEquals(projection.expected(), projection.low(), 1_000, "every day had the same pace");
        assertEquals(projection.expected(), projection.high(), 1_000);
        assertEquals(END, projection.band().getLast().at());
    }

    @Test
    void oneBigPurchaseDoesNotBendTheTrend() {
        List<Point> points = new ArrayList<>(growing(20, BILLION));
        int purchaseDay = 12;
        for (int i = purchaseDay; i < points.size(); i++) {
            points.set(i, new Point(points.get(i).at(), points.get(i).total() - 6 * BILLION));
        }

        Projection projection = project(points, List.of());

        assertEquals(BILLION, projection.pace().perDay(), 0.1 * BILLION,
                "the middle of the slopes ignores the one day the wallet dropped");
    }

    @Test
    void theBandWidensWithTimeAndHoldsTheExpectedValue() {
        List<Point> points = new ArrayList<>();
        double total = 10 * BILLION;
        double[] days = {1.4, 0.2, 2.1, 0.9, 1.6, 0.3, 1.8, 0.7, 1.1, 1.5, 0.4, 2.0, 0.8, 1.2, 0.6, 1.9, 1.0, 0.5};
        points.add(new Point(NOW.minus(Duration.ofDays(days.length)), total));
        for (int i = 0; i < days.length; i++) {
            total += days[i] * BILLION;
            points.add(new Point(NOW.minus(Duration.ofDays(days.length - 1 - i)), total));
        }

        Projection projection = project(points, List.of());
        List<Band> band = projection.band();
        Band week = band.get(7);
        Band last = band.getLast();

        assertTrue(last.high() - last.low() > week.high() - week.low(), "further ahead is less certain");
        for (Band step : band) {
            assertTrue(step.low() <= step.expected() + 1 && step.expected() <= step.high() + 1);
        }
        assertTrue(projection.low() < projection.expected() && projection.expected() < projection.high());
    }

    @Test
    void iskMovedFromTheCorporationWalletIsNotProduced() {
        Instant withdrawal = NOW.minus(Duration.ofDays(9)).minus(Duration.ofHours(1));
        List<Point> moved = growing(20, BILLION).stream()
                .map(point -> point.at().isAfter(withdrawal) ? new Point(point.at(), point.total() + 7 * BILLION)
                        : point)
                .toList();

        Projection projection = project(moved, List.of(new Transfer(withdrawal, 7 * BILLION)));

        assertEquals(BILLION, projection.pace().perDay(), DELTA);
        assertEquals(36 * BILLION, projection.latest().total(), DELTA, "the ISK is still there");
    }

    @Test
    void onlyTheLastSnapshotOfADayCounts() {
        List<Point> points = new ArrayList<>(growing(10, BILLION));
        points.add(new Point(NOW.minus(Duration.ofHours(2)), 999 * BILLION));
        points.sort((a, b) -> a.at().compareTo(b.at()));

        assertEquals(10, Forecast.lastOfEachDay(points, BUCHAREST).size());
    }

    @Test
    void thereIsNoPaceWithLessThanThreeDays() {
        Projection projection = project(growing(3, BILLION).subList(1, 3), List.of());

        assertNull(projection.pace());
        assertEquals(projection.latest().total(), projection.expected(), DELTA);
        assertTrue(projection.band().isEmpty());
    }

    @Test
    void withoutDataThereIsNothingToForecast() {
        assertTrue(project(List.of(), List.of()).isEmpty());
    }

    @Test
    void aGoalSaysWhenItIsReachedAndWhatItTakes() {
        Projection projection = project(growing(20, BILLION), List.of());

        Goal goal = projection.goal(49 * BILLION);
        assertNotNull(goal.expected());
        assertEquals(20, Duration.between(projection.latest().at(), goal.expected()).toHours() / 24.0, 0.01);
        double needed = (49 * BILLION - 29 * BILLION) / daysLeft();
        assertEquals(needed, goal.neededPerDay(), DELTA);
        assertTrue(projection.goal(20 * BILLION).reached());
        assertNull(projection.goal(5_000 * BILLION).likely(), "not within the forecast");
    }

    @Test
    void aFallingPaceNeverReachesAHigherGoal() {
        Projection projection = project(growing(20, -BILLION), List.of());

        Goal goal = projection.goal(100 * BILLION);
        assertNull(goal.expected());
        assertTrue(goal.neededPerDay() > 0);
    }
}
