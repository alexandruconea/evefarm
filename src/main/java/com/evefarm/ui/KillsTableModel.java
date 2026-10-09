package com.evefarm.ui;

import com.evefarm.model.KillDayTypeRow;
import com.evefarm.ui.column.ColumnDef;
import com.evefarm.ui.column.ColumnTableModel;

import java.util.List;

public final class KillsTableModel extends ColumnTableModel<KillDayTypeRow> {

    private static final List<ColumnDef<KillDayTypeRow>> COLUMNS = List.of(
            new ColumnDef<>("date", "Date", String.class, r -> r.date().toString(),
                    "The day of the kills, in your local time"),
            new ColumnDef<>("faction", "Faction", String.class, KillDayTypeRow::factionLabel,
                    "The faction of the NPCs"),
            new ColumnDef<>("type", "Ship Type", String.class, KillDayTypeRow::npcName,
                    "The NPC ship type"),
            new ColumnDef<>("system", "System", String.class, KillDayTypeRow::solarSystem,
                    "The solar system of the kills"),
            new ColumnDef<>("count", "Kills", Integer.class, KillDayTypeRow::count,
                    "How many your characters killed")
    );

    public KillsTableModel() {
        super(COLUMNS);
    }
}
