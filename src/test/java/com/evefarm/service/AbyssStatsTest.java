package com.evefarm.service;

import com.evefarm.model.AbyssTier;
import com.evefarm.model.AbyssWeather;
import com.evefarm.model.AbyssalRun;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AbyssStatsTest {

    private static final List<AbyssalRun> RUNS = List.of(
            run(AbyssTier.FIERCE, AbyssWeather.EXOTIC, 900, true, 60_000_000, 10_000_000.0),
            run(AbyssTier.FIERCE, AbyssWeather.DARK, 1_200, true, 40_000_000, 10_000_000.0),
            run(AbyssTier.RAGING, AbyssWeather.EXOTIC, 600, false, 0, 30_000_000.0),
            run(AbyssTier.FIERCE, AbyssWeather.EXOTIC, null, true, 20_000_000, null));

    @Test
    void theOverallSummaryAddsUpEveryRun() {
        AbyssStats.Summary overall = AbyssStats.overall(RUNS);

        assertEquals(4, overall.runs());
        assertEquals(1, overall.deaths());
        assertEquals(120_000_000.0, overall.loot());
        assertEquals(50_000_000.0, overall.filamentCost());
        assertEquals(70_000_000.0, overall.profit());
        assertEquals(17_500_000.0, overall.averageProfit());
        assertEquals(900.0, overall.averageSeconds());
        assertEquals(50_000_000.0 * 3600 / 2_700, overall.iskPerHour(), 0.001);
    }

    @Test
    void runsAreGroupedByTierInTierOrder() {
        List<AbyssStats.Summary> byTier = AbyssStats.byTier(RUNS);

        assertEquals(List.of("T3 Fierce", "T4 Raging"), byTier.stream().map(AbyssStats.Summary::label).toList());
        assertEquals(3, byTier.get(0).runs());
        assertEquals(100_000_000.0, byTier.get(0).profit());
        assertEquals(80_000_000.0 * 3600 / 2_100, byTier.get(0).iskPerHour(), 0.001);
        assertEquals(1, byTier.get(1).deaths());
        assertEquals(-30_000_000.0, byTier.get(1).profit());
    }

    @Test
    void runsAreGroupedByFilament() {
        List<AbyssStats.Summary> byFilament = AbyssStats.byFilament(RUNS);

        assertEquals(List.of("T3 Fierce Dark", "T3 Fierce Exotic", "T4 Raging Exotic"),
                byFilament.stream().map(AbyssStats.Summary::label).toList());
        assertEquals(2, byFilament.get(1).runs());
    }

    @Test
    void runsWithoutATimeHaveNoIskPerHour() {
        AbyssStats.Summary summary = AbyssStats.overall(List.of(run(null, null, null, true, 5, null)));

        assertNull(summary.iskPerHour());
        assertNull(summary.averageSeconds());
        assertEquals(List.of("Unknown"), AbyssStats.byFilament(List.of(run(null, null, null, true, 5, null)))
                .stream().map(AbyssStats.Summary::label).toList());
    }

    @Test
    void noRunsGiveAnEmptySummary() {
        AbyssStats.Summary summary = AbyssStats.overall(List.of());

        assertEquals(0, summary.runs());
        assertEquals(0.0, summary.averageProfit());
    }

    private static AbyssalRun run(AbyssTier tier, AbyssWeather weather, Integer seconds, boolean survived,
                                  double loot, Double filament) {
        return new AbyssalRun(0, 1, "Pilot", Instant.parse("2026-09-28T18:00:00Z"), seconds, tier, weather, null,
                null, survived, loot, filament, null);
    }
}
