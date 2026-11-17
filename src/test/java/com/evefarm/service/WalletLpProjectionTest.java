package com.evefarm.service;

import com.evefarm.service.WalletLpProjection.Point;
import com.evefarm.service.WalletLpProjection.Projection;
import com.evefarm.service.WalletLpProjection.Transfer;
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

class WalletLpProjectionTest {

    private static final ZoneId BUCHAREST = ZoneId.of("Europe/Bucharest");
    private static final Instant NOW = Instant.parse("2026-11-17T18:00:00Z");
    private static final double BILLION = 1_000_000_000;
    private static final double DELTA = 1;

    private static List<Point> growing(int days, double perDay) {
        List<Point> points = new ArrayList<>();
        for (int day = days - 1; day >= 0; day--) {
            double total = 10 * BILLION + perDay * (days - 1 - day);
            points.add(new Point(NOW.minus(Duration.ofDays(day)), total * 0.8, total * 0.2));
        }
        return points;
    }

    @Test
    void aSteadyPaceGoesOnUntilTheEndOfTheYear() {
        Projection projection = WalletLpProjection.project(growing(20, BILLION), List.of(), NOW, BUCHAREST);

        assertEquals(Instant.parse("2026-12-31T22:00:00Z"), projection.end(), "midnight on Jan 1 in Bucharest");
        assertEquals(BILLION, projection.pace().perDay(), DELTA);
        assertEquals(29 * BILLION, projection.latest().total(), DELTA);
        double daysLeft = Duration.between(NOW, projection.end()).toSeconds() / 86_400.0;
        assertEquals(29 * BILLION + daysLeft * BILLION, projection.expected(), DELTA);
        assertEquals(projection.expected(), projection.low(), DELTA, "every week had the same pace");
        assertEquals(projection.expected(), projection.high(), DELTA);
    }

    @Test
    void iskMovedFromTheCorporationWalletIsNotProduced() {
        List<Point> points = new ArrayList<>(growing(20, BILLION));
        Instant withdrawal = NOW.minus(Duration.ofDays(9)).minus(Duration.ofHours(1));
        List<Point> moved = points.stream()
                .map(point -> point.at().isAfter(withdrawal)
                        ? new Point(point.at(), point.wallet() + 7 * BILLION, point.lpValue()) : point)
                .toList();

        Projection projection = WalletLpProjection.project(moved, List.of(new Transfer(withdrawal, 7 * BILLION)),
                NOW, BUCHAREST);

        assertEquals(BILLION, projection.pace().perDay(), DELTA);
        assertEquals(36 * BILLION, projection.latest().total(), DELTA, "the ISK is still in the wallet");
    }

    @Test
    void iskDonatedToAnotherCharacterWasStillProduced() {
        Instant donation = NOW.minus(Duration.ofDays(4)).minus(Duration.ofHours(1));
        List<Point> points = growing(20, BILLION).stream()
                .map(point -> point.at().isAfter(donation)
                        ? new Point(point.at(), point.wallet() - 2 * BILLION, point.lpValue()) : point)
                .toList();

        Projection projection = WalletLpProjection.project(points, List.of(new Transfer(donation, -2 * BILLION)),
                NOW, BUCHAREST);

        assertEquals(BILLION, projection.pace().perDay(), DELTA);
    }

    @Test
    void theBandIsThePaceOfTheSlowestAndTheFastestWeek() {
        List<Point> points = new ArrayList<>();
        double total = 10 * BILLION;
        for (int day = 21; day >= 0; day--) {
            points.add(new Point(NOW.minus(Duration.ofDays(day)), total, 0));
            total += day > 14 ? 0.5 * BILLION : day > 7 ? BILLION : 2 * BILLION;
        }

        Projection projection = WalletLpProjection.project(points, List.of(), NOW, BUCHAREST);

        assertNotNull(projection.pace().weeks());
        assertEquals(0.5 * BILLION, projection.pace().weeks().slowest(), DELTA);
        assertEquals(2 * BILLION, projection.pace().weeks().fastest(), DELTA);
        assertTrue(projection.low() < projection.expected());
        assertTrue(projection.high() > projection.expected());
    }

    @Test
    void onlyTheLastSnapshotOfADayCounts() {
        List<Point> points = new ArrayList<>(growing(5, BILLION));
        points.add(new Point(NOW.minus(Duration.ofHours(3)), 100 * BILLION, 0));

        Projection projection = WalletLpProjection.project(points, List.of(), NOW, BUCHAREST);

        assertEquals(5, projection.history().size());
        assertEquals(14 * BILLION, projection.latest().total(), DELTA, "the snapshot at 18:00 is the day's last");
    }

    @Test
    void thereIsNoPaceWithLessThanThreeDays() {
        Projection projection = WalletLpProjection.project(growing(2, BILLION), List.of(), NOW, BUCHAREST);

        assertNull(projection.pace());
        assertEquals(projection.latest().total(), projection.expected());
    }

    @Test
    void inJanuaryThePaceStillUsesDecemberButTheChartStartsWithTheYear() {
        Instant january = Instant.parse("2027-01-05T12:00:00Z");
        List<Point> points = new ArrayList<>();
        for (int day = 20; day >= 0; day--) {
            points.add(new Point(january.minus(Duration.ofDays(day)), 10 * BILLION + (20 - day) * BILLION, 0));
        }

        Projection projection = WalletLpProjection.project(points, List.of(), january, BUCHAREST);

        assertEquals(BILLION, projection.pace().perDay(), DELTA);
        assertTrue(projection.history().stream().allMatch(point -> point.at().isAfter(Instant.parse(
                "2026-12-31T22:00:00Z"))));
        assertEquals(Instant.parse("2027-12-31T22:00:00Z"), projection.end());
    }

    @Test
    void withoutSnapshotsThereIsNothingToProject() {
        assertTrue(WalletLpProjection.project(List.of(), List.of(), NOW, BUCHAREST).isEmpty());
    }
}
