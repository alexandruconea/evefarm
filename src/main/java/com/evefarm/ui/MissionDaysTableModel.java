package com.evefarm.ui;

import com.evefarm.service.MissionIncome.DayTotals;
import com.evefarm.service.MissionIncome.Grouping;
import com.evefarm.ui.column.ColumnDef;
import com.evefarm.ui.column.ColumnTableModel;
import com.evefarm.ui.column.Isk;
import com.evefarm.util.Text;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public final class MissionDaysTableModel extends ColumnTableModel<MissionDaysTableModel.Row> {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd EEE", Locale.US);
    private static final DateTimeFormatter WEEK = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US);

    record Row(DayTotals day, Grouping grouping) {

        When when() {
            LocalDate date = day.date();
            return new When(date, switch (grouping) {
                case DAY -> DAY.format(date);
                case WEEK -> "Week of " + WEEK.format(date);
                case MONTH -> MONTH.format(date);
            });
        }
    }

    record When(LocalDate date, String text) implements Comparable<When> {

        @Override
        public int compareTo(When other) {
            return date.compareTo(other.date);
        }

        @Override
        public String toString() {
            return text;
        }
    }

    private static final List<ColumnDef<Row>> COLUMNS = List.of(
            new ColumnDef<>("day", "Date", When.class, Row::when,
                    "The day, the week or the month, in your local time"),
            new ColumnDef<>("missions", "Missions", Integer.class, r -> r.day().totals().missions(),
                    "How many missions were turned in"),
            new ColumnDef<>("rewards", "Rewards", Isk.class, false, r -> Isk.of(r.day().totals().rewards()),
                    "The ISK reward of the missions"),
            new ColumnDef<>("timeBonuses", "Time Bonuses", Isk.class, false,
                    r -> Isk.of(r.day().totals().timeBonuses()),
                    "The bonus for finishing missions in time"),
            new ColumnDef<>("bounties", "Bounties", Isk.class, r -> Isk.of(r.day().totals().bounties()),
                    "Bounties for NPCs killed in missions: mission NPCs such as burners, and other NPCs paid "
                            + "close to a mission turn-in"),
            new ColumnDef<>("isk", "Mission ISK", Isk.class, r -> Isk.of(r.day().totals().isk()),
                    "Rewards, time bonuses and bounties together"),
            new ColumnDef<>("lp", "LP Earned", String.class, r -> Text.count(r.day().totals().lp()),
                    "The LP your characters earned, with every corporation"),
            new ColumnDef<>("otherBounties", "Other Bounties", Isk.class,
                    r -> Isk.of(r.day().totals().otherBounties()),
                    "Bounties far from any mission turn-in, such as ratting. They don't count as mission ISK")
    );

    public MissionDaysTableModel() {
        super(COLUMNS);
    }

    static String title(Grouping grouping) {
        return switch (grouping) {
            case DAY -> "By day";
            case WEEK -> "By week";
            case MONTH -> "By month";
        };
    }
}
