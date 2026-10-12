package com.evefarm.service;

import com.evefarm.model.LpGain;
import com.evefarm.service.LpProduction.Day;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LpProductionTest {

    private static final ZoneId BUCHAREST = ZoneId.of("Europe/Bucharest");
    private static final LocalDate OCT_10 = LocalDate.of(2026, 10, 10);
    private static final LocalDate OCT_11 = LocalDate.of(2026, 10, 11);
    private static final LocalDate OCT_12 = LocalDate.of(2026, 10, 12);

    private static LpGain gain(String seenAt, String since, long points) {
        return new LpGain(2124165849L, "Malpais Legate", "Corporate Police Force", points,
                Instant.parse(seenAt), since == null ? null : Instant.parse(since));
    }

    @Test
    void lpCountsOnTheLocalDayItWasSeen() {
        Map<LocalDate, Day> days = LpProduction.byDay(List.of(
                gain("2026-10-10T20:30:00Z", "2026-10-10T19:30:00Z", 1_000),
                gain("2026-10-10T21:30:00Z", "2026-10-10T20:30:00Z", 2_000),
                gain("2026-10-11T09:00:00Z", "2026-10-11T08:00:00Z", 500)), BUCHAREST);

        assertEquals(List.of(OCT_10, OCT_11), List.copyOf(days.keySet()));
        assertEquals(1_000, days.get(OCT_10).points());
        assertEquals(2_500, days.get(OCT_11).points(), "00:30 in Bucharest is already the next day");
        assertEquals(2, days.get(OCT_11).gains().size());
        assertFalse(days.get(OCT_11).includesEarlierDays(), "read an hour before, even across midnight");
    }

    @Test
    void lpSeenAfterEveFarmWasClosedIncludesEarlierDays() {
        Day day = LpProduction.byDay(List.of(
                gain("2026-10-12T06:00:00Z", "2026-10-09T18:20:00Z", 30_000),
                gain("2026-10-12T07:00:00Z", "2026-10-12T06:00:00Z", 1_000)), BUCHAREST).get(OCT_12);

        assertEquals(31_000, day.points());
        assertTrue(day.includesEarlierDays());
        assertEquals(Instant.parse("2026-10-09T18:20:00Z"), day.earnedSince());
    }

    @Test
    void aLongGapWithinOneDayOrAnUnknownOneIsNotMarked() {
        Map<LocalDate, Day> days = LpProduction.byDay(List.of(
                gain("2026-10-12T18:00:00Z", "2026-10-12T05:00:00Z", 5_000),
                gain("2026-10-11T18:00:00Z", null, 700)), BUCHAREST);

        assertNull(days.get(OCT_12).earnedSince());
        assertFalse(days.get(OCT_11).includesEarlierDays());
    }

    @Test
    void totalsAndTheBestDayCoverOnlyTheChosenDays() {
        Map<LocalDate, Day> days = LpProduction.byDay(List.of(
                gain("2026-10-10T12:00:00Z", null, 9_000),
                gain("2026-10-11T12:00:00Z", null, 2_000),
                gain("2026-10-12T12:00:00Z", null, 3_000)), BUCHAREST);

        assertEquals(5_000, LpProduction.total(days, OCT_11, OCT_12));
        assertEquals(OCT_12, LpProduction.best(days, OCT_11, OCT_12).orElseThrow().date());
        assertEquals(OCT_10, LpProduction.best(days, OCT_10, OCT_12).orElseThrow().date());
    }

    @Test
    void eachShadeHoldsAboutAQuarterOfTheDaysWithLp() {
        LpProduction.Scale scale = LpProduction.scale(List.of(500L, 500L, 1_000L, 1_000L, 2_000L, 95_769L,
                182_538L, 914_190L, 0L));

        assertEquals(List.of(500L, 1_000L, 95_769L), scale.thresholds());
        assertEquals(List.of(0, 1, 2, 3, 3, 4, 4), LongStream.of(0, 500, 1_000, 2_000, 95_769, 182_538, 914_190)
                .mapToObj(scale::level).toList(), "one big day doesn't make the others pale");
    }

    @Test
    void theBestDayIsAlwaysTheDarkest() {
        assertEquals(4, LpProduction.scale(List.of(500L)).level(500));
        assertEquals(List.of(1, 4), LongStream.of(500, 9_000).mapToObj(LpProduction.scale(List.of(500L, 9_000L))::level)
                .toList());
        assertEquals(0, LpProduction.scale(List.of()).level(0));
    }
}
