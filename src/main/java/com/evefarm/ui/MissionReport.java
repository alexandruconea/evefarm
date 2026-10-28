package com.evefarm.ui;

import com.evefarm.service.MissionIncome.AgentTotals;
import com.evefarm.service.MissionIncome.CorporationLp;
import com.evefarm.service.MissionIncome.DayTotals;
import com.evefarm.service.MissionIncome.Period;
import com.evefarm.service.MissionIncome.Report;
import com.evefarm.service.MissionIncome.Totals;
import com.evefarm.util.DateUtil;
import com.evefarm.util.IskFormatter;
import com.evefarm.util.Text;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class MissionReport {

    static final String BOUNTY_RULE = "A bounty counts when it is for mission NPCs, such as burners, or when EVE paid "
            + "it up to 30 minutes after or an hour before a mission turn-in of the same character";

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US);
    private static final DateTimeFormatter WEEK_START = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US);
    private static final String GAP = "   ";

    record Line(String label, String value, boolean detail, String tip) {
    }

    private MissionReport() {
    }

    static String periodName(Period period, LocalDate from, LocalDate to) {
        return switch (period) {
            case DAY -> DAY.format(from);
            case WEEK -> (from.getYear() == to.getYear() ? WEEK_START.format(from) : DAY.format(from)) + " to "
                    + DAY.format(to);
            case MONTH -> MONTH.format(from);
        };
    }

    static List<Line> summary(Report report, double targetIskPerLp) {
        Totals totals = report.totals();
        List<Line> lines = new ArrayList<>();
        lines.add(new Line("Missions turned in", String.valueOf(totals.missions()), false,
                "Counted from the rewards and time bonuses in your wallet journal"));
        lines.add(new Line("ISK from missions", IskFormatter.format(totals.isk()), false,
                "Mission rewards, time bonuses and the bounties of the NPCs killed in missions, after corporation "
                        + "tax, from your wallet journal"));
        lines.add(new Line("Mission rewards", IskFormatter.format(totals.rewards()), true,
                "The ISK reward of the missions turned in"));
        lines.add(new Line("Time bonuses", IskFormatter.format(totals.timeBonuses()), true,
                "The bonus for finishing missions in time"));
        lines.add(new Line("Bounties for mission NPCs", IskFormatter.format(totals.missionNpcBounties()), true,
                "Bounties for NPCs that only spawn in missions, such as burners"));
        lines.add(new Line("Other bounties near a turn-in", IskFormatter.format(totals.nearTurnInBounties()), true,
                "Bounties for other NPCs that EVE paid up to 30 minutes after or an hour before a mission turn-in, "
                        + "such as the NPCs of level 5 missions"));
        lines.add(new Line("LP earned", lp(totals.lp()), false,
                "The LP your characters earned, with every corporation"));
        for (CorporationLp corporation : report.corporations()) {
            lines.add(new Line(corporation.corporationName(), lp(corporation.points()), true,
                    "The LP earned with " + corporation.corporationName()));
        }
        if (targetIskPerLp > 0) {
            String target = String.format(Locale.US, "%,.0f ISK/LP", targetIskPerLp);
            double lpValue = totals.lp() * targetIskPerLp;
            if (totals.lp() > 0) {
                lines.add(new Line("Worth at " + target, IskFormatter.format(lpValue), true,
                        "The LP times your target, set with Set ISK/LP Target... in Offers"));
            }
            lines.add(new Line("ISK and LP together", IskFormatter.format(totals.isk() + lpValue), false,
                    "ISK from missions plus the LP at " + target));
        }
        if (totals.otherBounties() > 0) {
            lines.add(new Line("Not counted: other bounties", IskFormatter.format(totals.otherBounties()), false,
                    "Bounties far from any mission turn-in, such as ratting"));
        }
        return lines;
    }

    static List<String> lines(Report report, Period period, String characters, double targetIskPerLp,
                              List<String> notes, Instant saved) {
        List<String> lines = new ArrayList<>();
        lines.add("EVE Farm - Mission Report");
        lines.add(period + ": " + periodName(period, report.from(), report.to()));
        lines.add("Characters: " + characters);
        lines.add("Saved " + DateUtil.formatEveMinute(saved) + " EVE");
        lines.add("");
        List<List<String>> summary = new ArrayList<>();
        for (Line line : summary(report, targetIskPerLp)) {
            summary.add(List.of((line.detail() ? "  " : "") + line.label(), line.value()));
        }
        lines.addAll(columns(List.of("", ""), summary, false));
        if (!notes.isEmpty()) {
            lines.add("");
            lines.addAll(notes);
        }
        lines.add(BOUNTY_RULE + ".");

        if (!report.days().isEmpty()) {
            lines.add("");
            lines.add("By day");
            List<List<String>> days = new ArrayList<>();
            for (DayTotals day : report.days()) {
                Totals totals = day.totals();
                days.add(List.of(day.date().toString(), String.valueOf(totals.missions()),
                        IskFormatter.formatPlain(totals.rewards()), IskFormatter.formatPlain(totals.timeBonuses()),
                        IskFormatter.formatPlain(totals.bounties()), IskFormatter.formatPlain(totals.isk()),
                        Text.count(totals.lp())));
            }
            lines.addAll(columns(List.of("Day", "Missions", "Rewards", "Time Bonuses", "Bounties", "Mission ISK",
                    "LP"), days, true));
        }
        if (!report.agents().isEmpty()) {
            lines.add("");
            lines.add("By agent");
            List<List<String>> agents = new ArrayList<>();
            for (AgentTotals agent : report.agents()) {
                agents.add(List.of(MissionAgentsTableModel.agent(agent), String.valueOf(agent.missions()),
                        IskFormatter.formatPlain(agent.rewards()), IskFormatter.formatPlain(agent.timeBonuses()),
                        IskFormatter.formatPlain(agent.bounties()), IskFormatter.formatPlain(agent.isk()),
                        agent.missions() == 0 ? "" : IskFormatter.formatPlain(agent.perMission())));
            }
            lines.addAll(columns(List.of("Agent", "Missions", "Rewards", "Time Bonuses", "Bounties", "Mission ISK",
                    "Per Mission"), agents, true));
        }
        return lines;
    }

    private static List<String> columns(List<String> headers, List<List<String>> rows, boolean withHeaders) {
        int[] widths = new int[headers.size()];
        List<List<String>> all = new ArrayList<>();
        if (withHeaders) {
            all.add(headers);
        }
        all.addAll(rows);
        for (List<String> row : all) {
            for (int i = 0; i < row.size(); i++) {
                widths[i] = Math.max(widths[i], row.get(i).length());
            }
        }
        List<String> lines = new ArrayList<>();
        for (List<String> row : all) {
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < row.size(); i++) {
                String cell = row.get(i);
                if (i == 0) {
                    line.append(cell).append(" ".repeat(widths[i] - cell.length()));
                } else {
                    line.append(GAP).append(" ".repeat(widths[i] - cell.length())).append(cell);
                }
            }
            lines.add(line.toString().stripTrailing());
        }
        return lines;
    }

    private static String lp(long points) {
        return Text.count(points) + " LP";
    }
}
