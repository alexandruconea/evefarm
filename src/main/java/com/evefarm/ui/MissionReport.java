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
    private static final DateTimeFormatter MONTH_ONLY = DateTimeFormatter.ofPattern("MMMM", Locale.US);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);
    private static final String GAP = "   ";

    record Line(String label, String value, boolean detail, String tip) {
    }

    record Card(Line total, List<Line> rows) {
    }

    record Overview(Line headline, String caption, String captionTip, List<Card> cards, Line notCounted) {
    }

    private enum Unit {
        ISK, LP;

        String full(double value) {
            return this == ISK ? IskFormatter.format(value) : lp(Math.round(value));
        }

        String plain(double value) {
            return this == ISK ? IskFormatter.formatPlain(value) : Text.count(Math.round(value));
        }
    }

    private record Amount(String label, double value, Unit unit, String tip) {

        Line full(boolean detail) {
            return new Line(label, unit.full(value), detail, tip);
        }

        Line in(Unit cardUnit) {
            return new Line(label, unit == cardUnit ? unit.plain(value) : unit.full(value), true, tip);
        }
    }

    private record Section(Amount total, List<Amount> rows) {

        List<Line> lines() {
            List<Line> lines = new ArrayList<>();
            lines.add(total.full(false));
            rows.forEach(row -> lines.add(row.full(true)));
            return lines;
        }

        Card card() {
            return new Card(total.full(false), rows.stream().map(row -> row.in(total.unit())).toList());
        }
    }

    private record Parts(Line missions, Section isk, Section lp, Amount together, Amount notCounted) {
    }

    private MissionReport() {
    }

    static String periodName(Period period, LocalDate from, LocalDate to) {
        return switch (period) {
            case DAY -> DAY.format(from);
            case WEEK -> (from.getYear() == to.getYear() ? WEEK_START.format(from) : DAY.format(from)) + " to "
                    + DAY.format(to);
            case MONTH -> MONTH.format(from);
            case THREE_MONTHS, SIX_MONTHS -> (from.getYear() == to.getYear() ? MONTH_ONLY.format(from)
                    : MONTH.format(from)) + " to " + MONTH.format(to);
            case YEAR -> String.valueOf(from.getYear());
            case ALL_TIME -> from.equals(to) ? DATE.format(from) : DATE.format(from) + " to " + DATE.format(to);
        };
    }

    private static Parts parts(Report report, double targetIskPerLp) {
        Totals totals = report.totals();
        Line missions = new Line("Missions turned in", String.valueOf(totals.missions()), false,
                "Counted from the rewards and time bonuses in your wallet journal");
        Section isk = new Section(new Amount("ISK from missions", totals.isk(), Unit.ISK,
                "Mission rewards, time bonuses and the bounties of the NPCs killed in missions, after corporation "
                        + "tax, from your wallet journal"), List.of(
                new Amount("Mission rewards", totals.rewards(), Unit.ISK, "The ISK reward of the missions turned in"),
                new Amount("Time bonuses", totals.timeBonuses(), Unit.ISK, "The bonus for finishing missions in time"),
                new Amount("Bounties for mission NPCs", totals.missionNpcBounties(), Unit.ISK,
                        "Bounties for NPCs that only spawn in missions, such as burners"),
                new Amount("Other bounties near a turn-in", totals.nearTurnInBounties(), Unit.ISK,
                        "Bounties for other NPCs that EVE paid up to 30 minutes after or an hour before a mission "
                                + "turn-in, such as the NPCs of level 5 missions")));
        List<Amount> lpRows = new ArrayList<>();
        for (CorporationLp corporation : report.corporations()) {
            lpRows.add(new Amount(corporation.corporationName(), corporation.points(), Unit.LP,
                    "The LP earned with " + corporation.corporationName()));
        }
        Amount together = null;
        if (targetIskPerLp > 0) {
            String target = String.format(Locale.US, "%,.0f ISK/LP", targetIskPerLp);
            double lpValue = totals.lp() * targetIskPerLp;
            if (totals.lp() > 0) {
                lpRows.add(new Amount("Worth at " + target, lpValue, Unit.ISK,
                        "The LP times your target, set with Set ISK/LP Target... in Offers"));
            }
            together = new Amount("ISK and LP together", totals.isk() + lpValue, Unit.ISK,
                    "ISK from missions plus the LP at " + target);
        }
        Section lp = new Section(new Amount("LP earned", totals.lp(), Unit.LP,
                "The LP your characters earned, with every corporation"), lpRows);
        Amount notCounted = totals.otherBounties() > 0 ? new Amount("Not counted: other bounties",
                totals.otherBounties(), Unit.ISK, "Bounties far from any mission turn-in, such as ratting") : null;
        return new Parts(missions, isk, lp, together, notCounted);
    }

    static List<Line> summary(Report report, double targetIskPerLp) {
        Parts parts = parts(report, targetIskPerLp);
        List<Line> lines = new ArrayList<>();
        lines.add(parts.missions());
        lines.addAll(parts.isk().lines());
        lines.addAll(parts.lp().lines());
        if (parts.together() != null) {
            lines.add(parts.together().full(false));
        }
        if (parts.notCounted() != null) {
            lines.add(parts.notCounted().full(false));
        }
        return lines;
    }

    static Overview overview(Report report, double targetIskPerLp) {
        Parts parts = parts(report, targetIskPerLp);
        Amount headline = parts.together() == null ? parts.isk().total() : parts.together();
        int missions = report.totals().missions();
        String caption = missions == 1 ? "1 mission" : Text.count(missions) + " missions";
        if (missions > 0) {
            caption += "  ·  " + new CompactIskNumberFormat(1).format(headline.value() / missions)
                    + " ISK per mission";
        }
        String captionTip = parts.missions().tip() + (missions > 0 ? ". Per mission: " + headline.label()
                + " divided by the missions" : "");
        return new Overview(headline.full(false), caption, captionTip,
                List.of(parts.isk().card(), parts.lp().card()),
                parts.notCounted() == null ? null : parts.notCounted().full(false));
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
