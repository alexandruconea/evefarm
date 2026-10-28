package com.evefarm.ui;

import com.evefarm.service.MissionIncome.AgentTotals;
import com.evefarm.service.MissionIncome.CorporationLp;
import com.evefarm.service.MissionIncome.DayTotals;
import com.evefarm.service.MissionIncome.Period;
import com.evefarm.service.MissionIncome.Report;
import com.evefarm.service.MissionIncome.Totals;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissionReportTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 26);
    private static final Totals TOTALS = new Totals(3, 4_000_000, 3_000_000, 9_900_000, 6_000_000, 900_000, 12_000);
    private static final Report REPORT = new Report(MONDAY, MONDAY.plusDays(6), TOTALS,
            List.of(new DayTotals(MONDAY, TOTALS)),
            List.of(new AgentTotals("Yasonar Anoh", 3, 4_000_000, 3_000_000, 9_900_000),
                    new AgentTotals(null, 0, 0, 0, 6_000_000)),
            List.of(new CorporationLp("Carthum Conglomerate", 12_000)), null);

    @Test
    void periodsAreNamedTheWayTheyAreChosen() {
        assertEquals("Wed, Oct 28, 2026", MissionReport.periodName(Period.DAY, MONDAY.plusDays(2),
                MONDAY.plusDays(2)));
        assertEquals("Mon, Oct 26 to Sun, Nov 1, 2026", MissionReport.periodName(Period.WEEK, MONDAY,
                MONDAY.plusDays(6)));
        assertEquals("Mon, Dec 28, 2026 to Sun, Jan 3, 2027", MissionReport.periodName(Period.WEEK,
                LocalDate.of(2026, 12, 28), LocalDate.of(2027, 1, 3)));
        assertEquals("October 2026", MissionReport.periodName(Period.MONTH, LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31)));
    }

    @Test
    void theSummaryValuesTheLpAtTheTargetAndKeepsRattingApart() {
        List<String> summary = MissionReport.summary(REPORT, 1_000).stream()
                .map(line -> line.label() + " = " + line.value())
                .toList();

        assertEquals(List.of(
                "Missions turned in = 3",
                "ISK from missions = 22,900,000.00 ISK",
                "Mission rewards = 4,000,000.00 ISK",
                "Time bonuses = 3,000,000.00 ISK",
                "Bounties for mission NPCs = 9,900,000.00 ISK",
                "Other bounties near a turn-in = 6,000,000.00 ISK",
                "LP earned = 12,000 LP",
                "Carthum Conglomerate = 12,000 LP",
                "Worth at 1,000 ISK/LP = 12,000,000.00 ISK",
                "ISK and LP together = 34,900,000.00 ISK",
                "Not counted: other bounties = 900,000.00 ISK"), summary);
        assertTrue(MissionReport.summary(REPORT, 0).stream().noneMatch(line -> line.value().equals(
                "34,900,000.00 ISK")), "without a target the LP has no ISK value");
    }

    @Test
    void theSavedReportLinesUpItsColumns() {
        List<String> lines = MissionReport.lines(REPORT, Period.WEEK, "All characters", 0,
                List.of("A note."), Instant.parse("2026-10-28T22:00:00Z"));

        assertEquals(List.of("EVE Farm - Mission Report", "Week: Mon, Oct 26 to Sun, Nov 1, 2026",
                "Characters: All characters", "Saved 2026-10-28 22:00 EVE", ""), lines.subList(0, 5));
        assertTrue(lines.contains("ISK from missions                 22,900,000.00 ISK"));
        assertTrue(lines.contains("Missions turned in                                3"));
        assertTrue(lines.contains("  Mission rewards                  4,000,000.00 ISK"));
        assertTrue(lines.contains("A note."));
        int agents = lines.indexOf("By agent");
        assertEquals("Agent               Missions        Rewards   Time Bonuses       Bounties     Mission ISK"
                + "    Per Mission", lines.get(agents + 1));
        assertEquals("Yasonar Anoh               3   4,000,000.00   3,000,000.00   9,900,000.00   16,900,000.00"
                + "   5,633,333.33", lines.get(agents + 2));
        assertEquals("No turn-in nearby          0           0.00           0.00   6,000,000.00    6,000,000.00",
                lines.get(agents + 3));
    }
}
