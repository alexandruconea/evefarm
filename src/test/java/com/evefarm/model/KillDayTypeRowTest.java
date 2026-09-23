package com.evefarm.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KillDayTypeRowTest {

    @Test
    void countsIdenticalEventsIntoOneRow() {
        LocalDate day = LocalDate.of(2026, 3, 4);
        List<KillEvent> events = List.of(
                new KillEvent(day, "Sansha's Nation", "Sansha's Overlord", "Thashkarai"),
                new KillEvent(day, "Sansha's Nation", "Sansha's Overlord", "Thashkarai"),
                new KillEvent(day, "Sansha's Nation", "Sansha's Overlord", "Thashkarai"));

        List<KillDayTypeRow> rows = KillDayTypeRow.groupByDayTypeAndSystem(events);

        assertEquals(1, rows.size());
        assertEquals(3, rows.get(0).count());
    }

    @Test
    void sameTypeInTwoDifferentSystemsOnTheSameDayIsTwoRows() {
        LocalDate day = LocalDate.of(2026, 3, 4);
        List<KillEvent> events = List.of(
                new KillEvent(day, "Sansha's Nation", "Sansha's Overlord", "Thashkarai"),
                new KillEvent(day, "Sansha's Nation", "Sansha's Overlord", "Sacalan"));

        List<KillDayTypeRow> rows = KillDayTypeRow.groupByDayTypeAndSystem(events);

        assertEquals(2, rows.size());
    }

    @Test
    void unknownSystemGroupsUnderOneLabelInsteadOfSplittingNull() {
        LocalDate day = LocalDate.of(2026, 3, 4);
        List<KillEvent> events = List.of(
                new KillEvent(day, "Other / Unrecognized", "Burner Hawk", null),
                new KillEvent(day, "Other / Unrecognized", "Burner Hawk", null));

        List<KillDayTypeRow> rows = KillDayTypeRow.groupByDayTypeAndSystem(events);

        assertEquals(1, rows.size());
        assertEquals("Unknown", rows.get(0).solarSystem());
        assertEquals(2, rows.get(0).count());
    }

    @Test
    void sortedNewestDayFirst() {
        LocalDate earlier = LocalDate.of(2026, 3, 4);
        LocalDate later = LocalDate.of(2026, 3, 10);
        List<KillEvent> events = List.of(
                new KillEvent(earlier, "Other / Unrecognized", "Burner Hawk", "Onazel"),
                new KillEvent(later, "Other / Unrecognized", "Burner Hawk", "Onazel"));

        List<KillDayTypeRow> rows = KillDayTypeRow.groupByDayTypeAndSystem(events);

        assertEquals(later, rows.get(0).date());
        assertEquals(earlier, rows.get(1).date());
    }
}
