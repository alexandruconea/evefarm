package com.evefarm.ui;

import com.evefarm.model.SpawnRow;
import com.evefarm.ui.column.ColumnDef;
import com.evefarm.ui.column.ColumnTableModel;
import com.evefarm.util.DateUtil;
import com.evefarm.util.IskFormatter;

import java.util.List;

public final class SpawnsTableModel extends ColumnTableModel<SpawnRow> {

    private static final List<ColumnDef<SpawnRow>> COLUMNS = List.of(
            new ColumnDef<>("time", "Time (EVE)", String.class, r -> DateUtil.formatEveMinute(r.startedAt())),
            new ColumnDef<>("character", "Character", String.class, SpawnRow::characterName),
            new ColumnDef<>("system", "System", String.class, r -> r.solarSystem() == null ? "" : r.solarSystem()),
            new ColumnDef<>("kind", "Kind", String.class, SpawnRow::kind),
            new ColumnDef<>("killed", "Killed", Integer.class, SpawnRow::killed),
            new ColumnDef<>("bounty", "Bounty", String.class,
                    r -> r.bounty() > 0 ? IskFormatter.format(r.bounty()) : ""),
            new ColumnDef<>("duration", "Duration", String.class, r -> duration(r.durationSeconds())),
            new ColumnDef<>("composition", "NPCs", String.class, SpawnRow::composition)
    );

    public SpawnsTableModel() {
        super(COLUMNS);
    }

    static String duration(long seconds) {
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }
}
