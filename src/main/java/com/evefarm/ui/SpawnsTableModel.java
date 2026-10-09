package com.evefarm.ui;

import com.evefarm.model.SpawnRow;
import com.evefarm.ui.column.ColumnDef;
import com.evefarm.ui.column.ColumnTableModel;
import com.evefarm.util.DateUtil;
import com.evefarm.util.IskFormatter;

import java.util.List;
import java.util.Locale;

public final class SpawnsTableModel extends ColumnTableModel<SpawnRow> {

    private static final List<ColumnDef<SpawnRow>> COLUMNS = List.of(
            new ColumnDef<>("time", "Time (EVE)", String.class, r -> DateUtil.formatEveMinute(r.startedAt()),
                    "When the fight started, in EVE time"),
            new ColumnDef<>("character", "Characters", String.class, SpawnRow::characterName,
                    "The characters who fought the spawn"),
            new ColumnDef<>("system", "System", String.class, r -> r.solarSystem() == null ? "" : r.solarSystem(),
                    "The solar system of the fight"),
            new ColumnDef<>("kind", "Kind", String.class, SpawnRow::kind,
                    "The strongest NPC of the spawn: Officer, Commander, Belt rat, Missions or Other"),
            new ColumnDef<>("killed", "Killed", Integer.class, SpawnRow::killed,
                    "How many NPCs your characters killed"),
            new ColumnDef<>("bounty", "Bounty", String.class,
                    r -> r.bounty() > 0 ? IskFormatter.format(r.bounty()) : "",
                    "The bounty of the NPCs killed"),
            new ColumnDef<>("duration", "Duration", String.class, r -> duration(r.durationSeconds()),
                    "How long the fight took, in minutes and seconds"),
            new ColumnDef<>("composition", "NPCs", String.class, SpawnRow::composition,
                    "The NPCs killed, with how many of each")
    );

    public SpawnsTableModel() {
        super(COLUMNS);
    }

    static String duration(long seconds) {
        return String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60);
    }
}
