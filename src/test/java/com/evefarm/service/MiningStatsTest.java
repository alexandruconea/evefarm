package com.evefarm.service;

import com.evefarm.model.MiningRow;
import com.evefarm.service.MiningStats.GroupBy;
import com.evefarm.service.MiningStats.Summary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MiningStatsTest {

    private static MiningRow mined(String character, String date, String ore, long quantity, double unitValue) {
        return new MiningRow(1, character, date, "Jita", 1, ore, "Ore", quantity, quantity * 0.1, unitValue);
    }

    private static final List<MiningRow> ROWS = List.of(
            mined("Nozeu", "2026-09-28", "Veldspar", 10_000, 20),
            mined("Barset", "2026-09-28", "Scordite", 5_000, 30),
            mined("Nozeu", "2026-09-29", "Veldspar", 20_000, 20));

    @Test
    void daysAreListedNewestFirst() {
        List<Summary> days = MiningStats.grouped(ROWS, GroupBy.DAY);

        assertEquals(List.of("2026-09-29", "2026-09-28"), days.stream().map(Summary::label).toList());
        assertEquals(350_000.0, days.get(1).value(), 1e-9);
    }

    @Test
    void otherGroupsAreListedByValue() {
        List<Summary> ores = MiningStats.grouped(ROWS, GroupBy.ORE);

        assertEquals(List.of("Veldspar", "Scordite"), ores.stream().map(Summary::label).toList());
        assertEquals(600_000.0, ores.getFirst().value(), 1e-9);
        assertEquals(2, ores.getFirst().days());
    }

    @Test
    void theOverallSummaryAveragesOverTheDaysMined() {
        Summary overall = MiningStats.overall(ROWS);

        assertEquals(3_500.0, overall.volume(), 1e-9);
        assertEquals(750_000.0, overall.value(), 1e-9);
        assertEquals(2, overall.days());
        assertEquals(375_000.0, overall.valuePerDay(), 1e-9);
    }
}
