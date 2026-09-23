package com.evefarm.ui;

import com.evefarm.model.KillDayTypeRow;
import com.evefarm.ui.column.ColumnDef;
import com.evefarm.ui.column.ColumnTableModel;

import java.util.List;

public final class KillsTableModel extends ColumnTableModel<KillDayTypeRow> {

    private static final List<ColumnDef<KillDayTypeRow>> COLUMNS = List.of(
            new ColumnDef<>("date", "Date", String.class, r -> r.date().toString()),
            new ColumnDef<>("faction", "Faction", String.class, KillDayTypeRow::factionLabel),
            new ColumnDef<>("type", "Ship Type", String.class, KillDayTypeRow::npcName),
            new ColumnDef<>("system", "System", String.class, KillDayTypeRow::solarSystem),
            new ColumnDef<>("count", "Kills", Integer.class, KillDayTypeRow::count)
    );

    public KillsTableModel() {
        super(COLUMNS);
    }
}
