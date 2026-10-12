package com.evefarm.ui;

import com.evefarm.model.LpGain;
import com.evefarm.service.LpProduction;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LpProductionPanelTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);

    private static LpGain earned(LocalDate day, long points) {
        return new LpGain(2124165849L, "Malpais Legate", "Corporate Police Force", points,
                day.atTime(12, 0).toInstant(ZoneOffset.UTC), null);
    }

    @Test
    void theSummaryAddsUpTheLastWeekAndMonth() {
        Map<LocalDate, LpProduction.Day> days = LpProduction.byDay(List.of(
                earned(TODAY, 10_000), earned(TODAY.minusDays(6), 5_000), earned(TODAY.minusDays(7), 20_000),
                earned(TODAY.minusDays(40), 1_000)), ZoneOffset.UTC);

        assertEquals("Last 7 days: 15,000 LP  ·  Last 30 days: 35,000 LP, about 28M ISK at 800 ISK/LP  ·  "
                + "Best day: Oct 5, 20,000 LP", LpProductionPanel.summary(days, TODAY, 800));
    }

    @Test
    void withoutLpTheSummarySaysWhenItIsRead() {
        assertTrue(LpProductionPanel.summary(Map.of(), TODAY, 800).contains("once an hour"));
    }

    @Test
    void theSyncLineSaysHowFreshTheLpIs() {
        Instant now = Instant.parse("2026-10-12T15:20:00Z");

        assertEquals("Matches EVE as of 15:05  ·  next read at 16:06  ·  EVE renews LP once an hour",
                LpProductionPanel.syncText(Instant.parse("2026-10-12T15:05:00Z"),
                        Instant.parse("2026-10-12T16:06:00Z"), now, ZoneOffset.UTC));
        assertEquals("Matches EVE as of Oct 11, 22:00  ·  next read in a few minutes  ·  EVE renews LP once an hour",
                LpProductionPanel.syncText(Instant.parse("2026-10-11T22:00:00Z"),
                        Instant.parse("2026-10-11T23:00:30Z"), now, ZoneOffset.UTC));
        assertEquals("EVE Farm hasn't read your LP from EVE yet.",
                LpProductionPanel.syncText(null, null, now, ZoneOffset.UTC));
    }

    @Test
    void theColoursGoFromThePanelToTheAccent() {
        Color[] levels = LpHeatmap.levelColors(new Color(0x3aa9a3), Color.WHITE,
                new Color(0x101416));

        assertEquals(new Color(0x3aa9a3), levels[LpProduction.LEVELS]);
        assertTrue(levels[1].getGreen() < levels[2].getGreen() && levels[2].getGreen() < levels[3].getGreen());
    }
}
