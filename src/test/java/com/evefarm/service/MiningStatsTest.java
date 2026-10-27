package com.evefarm.service;

import com.evefarm.model.MiningRow;
import com.evefarm.model.RefinedMaterial;
import com.evefarm.service.MiningStats.GroupBy;
import com.evefarm.service.MiningStats.Summary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MiningStatsTest {

    private static MiningRow mined(String character, String date, String ore, long quantity, double unitValue) {
        return new MiningRow(1, character, date, "Jita", 1, ore, "Ore", quantity, quantity * 0.1, unitValue, 1,
                List.of());
    }

    private static MiningRow refined(String date, String ore, long quantity, List<RefinedMaterial> materials) {
        double value = materials.stream().mapToDouble(m -> m.value() == null ? 0 : m.value()).sum();
        return new MiningRow(1, "Nozeu", date, "Jita", 1, ore, "Ore", quantity, quantity * 0.1, value / quantity, 1,
                materials);
    }

    private static RefinedMaterial material(String name, double quantity, Double value) {
        return new RefinedMaterial(1, name, quantity, quantity * 0.01, value);
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
    void onlyOreAndMaterialGroupsCountUnits() {
        assertEquals(30_000.0, MiningStats.grouped(ROWS, GroupBy.ORE).getFirst().quantity());
        assertNull(MiningStats.grouped(ROWS, GroupBy.DAY).getFirst().quantity());
        assertNull(MiningStats.overall(ROWS).quantity());
    }

    @Test
    void refinedOreIsSplitIntoItsMaterials() {
        List<MiningRow> rows = List.of(
                refined("2026-09-28", "Veldspar", 1_000, List.of(material("Tritanium", 3_600, 14_400.0))),
                refined("2026-09-29", "Scordite", 1_000, List.of(material("Tritanium", 1_000, 4_000.0),
                        material("Pyerite", 500, 5_000.0))),
                mined("Nozeu", "2026-09-29", "Fullerite-C50", 10, 2_000));

        List<Summary> materials = MiningStats.grouped(rows, GroupBy.MATERIAL);

        assertEquals(List.of("Fullerite-C50", "Tritanium", "Pyerite"),
                materials.stream().map(Summary::label).toList(), "what isn't refined stays itself");
        Summary tritanium = materials.get(1);
        assertEquals(4_600.0, tritanium.quantity(), 1e-9);
        assertEquals(46.0, tritanium.volume(), 1e-9);
        assertEquals(18_400.0, tritanium.value(), 1e-9);
        assertEquals(2, tritanium.days());
        assertEquals(MiningStats.overall(rows).value(), materials.stream().mapToDouble(Summary::value).sum(), 1e-6);
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
