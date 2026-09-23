package com.evefarm.ui;

import com.evefarm.model.KillDayTypeRow;
import com.evefarm.model.OfficerSighting;
import com.evefarm.ui.column.ColumnDef;
import com.evefarm.ui.column.ColumnTableModel;
import com.evefarm.util.DateUtil;
import com.evefarm.util.IskFormatter;

import java.util.List;

public final class OfficersTableModel extends ColumnTableModel<OfficerSighting> {

    private static final List<ColumnDef<OfficerSighting>> COLUMNS = List.of(
            new ColumnDef<>("time", "EVE Time", String.class,
                    r -> DateUtil.formatEveTime(r.killed() ? r.killedAt() : r.firstSeenAt())),
            new ColumnDef<>("officer", "Officer", String.class, OfficerSighting::officerName),
            new ColumnDef<>("class", "Class", String.class, r -> shortGroupName(r.officerGroup())),
            new ColumnDef<>("result", "Result", String.class, r -> r.killed() ? "Killed" : "Not killed"),
            new ColumnDef<>("character", "Character", String.class, OfficerSighting::characterName),
            new ColumnDef<>("system", "System", String.class,
                    r -> r.solarSystem() == null ? KillDayTypeRow.UNKNOWN_SYSTEM : r.solarSystem()),
            new ColumnDef<>("belt", "Belt", String.class, r -> r.belt() == null ? "" : r.belt()),
            new ColumnDef<>("escort", "Escort Kills", Integer.class, OfficerSighting::escortKills),
            new ColumnDef<>("bounty", "Officer Bounty", String.class,
                    r -> r.officerBounty() > 0 ? IskFormatter.format(r.officerBounty()) : ""),
            new ColumnDef<>("drops", "Drops", String.class,
                    r -> r.dropValue() > 0 ? IskFormatter.format(r.dropValue()) : ""),
            new ColumnDef<>("total", "Total", String.class,
                    r -> r.totalValue() > 0 ? IskFormatter.format(r.totalValue()) : ""),
            new ColumnDef<>("payout", "Journal", String.class,
                    r -> r.payout() == null ? "" : "✓ " + DateUtil.formatEveClock(r.payout().paidAt()))
    );

    public OfficersTableModel() {
        super(COLUMNS);
    }

    static String shortGroupName(String groupName) {
        if (groupName == null) {
            return "";
        }
        return groupName.startsWith("Asteroid ") ? groupName.substring("Asteroid ".length()) : groupName;
    }
}
