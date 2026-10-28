package com.evefarm.ui;

import com.evefarm.service.MissionIncome.DayTotals;
import com.evefarm.ui.column.ColumnDef;
import com.evefarm.ui.column.ColumnTableModel;
import com.evefarm.util.IskFormatter;
import com.evefarm.util.Text;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public final class MissionDaysTableModel extends ColumnTableModel<DayTotals> {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd EEE", Locale.US);

    private static final List<ColumnDef<DayTotals>> COLUMNS = List.of(
            new ColumnDef<>("day", "Day", String.class, r -> DAY.format(r.date()),
                    "The day, in your local time"),
            new ColumnDef<>("missions", "Missions", Integer.class, r -> r.totals().missions(),
                    "How many missions were turned in"),
            new ColumnDef<>("rewards", "Rewards", String.class, false, r -> IskFormatter.format(r.totals().rewards()),
                    "The ISK reward of the missions"),
            new ColumnDef<>("timeBonuses", "Time Bonuses", String.class, false,
                    r -> IskFormatter.format(r.totals().timeBonuses()),
                    "The bonus for finishing missions in time"),
            new ColumnDef<>("bounties", "Bounties", String.class, r -> IskFormatter.format(r.totals().bounties()),
                    "Bounties for NPCs killed in missions: mission NPCs such as burners, and other NPCs paid "
                            + "close to a mission turn-in"),
            new ColumnDef<>("isk", "Mission ISK", String.class, r -> IskFormatter.format(r.totals().isk()),
                    "Rewards, time bonuses and bounties together"),
            new ColumnDef<>("lp", "LP Earned", String.class, r -> Text.count(r.totals().lp()),
                    "The LP your characters earned, with every corporation"),
            new ColumnDef<>("otherBounties", "Other Bounties", String.class,
                    r -> IskFormatter.format(r.totals().otherBounties()),
                    "Bounties far from any mission turn-in, such as ratting. They don't count as mission ISK")
    );

    public MissionDaysTableModel() {
        super(COLUMNS);
    }
}
