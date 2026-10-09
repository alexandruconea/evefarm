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
                    r -> DateUtil.formatEveTime(r.killed() ? r.killedAt() : r.firstSeenAt()),
                    "When the officer was killed, or first seen if it wasn't, in EVE time"),
            new ColumnDef<>("officer", "Officer", String.class, OfficerSighting::officerName,
                    "The officer's name"),
            new ColumnDef<>("class", "Class", String.class, r -> shortGroupName(r.officerGroup()),
                    "The officer's group, such as Serpentis Officer"),
            new ColumnDef<>("result", "Result", String.class, r -> r.killed() ? "Killed" : "Not killed",
                    "Whether your characters killed it"),
            new ColumnDef<>("character", "Characters", String.class, OfficerSighting::characterName,
                    "The characters who fought it"),
            new ColumnDef<>("system", "System", String.class,
                    r -> r.solarSystem() == null ? KillDayTypeRow.UNKNOWN_SYSTEM : r.solarSystem(),
                    "The solar system it was seen in"),
            new ColumnDef<>("belt", "Belt", String.class, r -> r.belt() == null ? "" : r.belt(),
                    "The asteroid belt it was in"),
            new ColumnDef<>("escort", "Escort Kills", Integer.class, OfficerSighting::escortKills,
                    "How many of its escorts your characters killed"),
            new ColumnDef<>("bounty", "Officer Bounty", String.class,
                    r -> r.officerBounty() > 0 ? IskFormatter.format(r.officerBounty()) : "",
                    "The officer's bounty"),
            new ColumnDef<>("drops", "Drops", String.class,
                    r -> r.dropValue() > 0 ? IskFormatter.format(r.dropValue()) : "",
                    "What the drops you recorded are worth"),
            new ColumnDef<>("total", "Total", String.class,
                    r -> r.totalValue() > 0 ? IskFormatter.format(r.totalValue()) : "",
                    "The officer bounty plus the drops"),
            new ColumnDef<>("payout", "Journal", String.class,
                    r -> r.payout() == null ? "" : "✓ " + DateUtil.formatEveClock(r.payout().paidAt()),
                    "A tick and the EVE time when your wallet journal shows the bounty was paid")
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
