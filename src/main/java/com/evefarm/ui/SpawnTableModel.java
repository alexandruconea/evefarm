package com.evefarm.ui;

import com.evefarm.model.SpawnMember;
import com.evefarm.ui.column.ColumnDef;
import com.evefarm.ui.column.ColumnTableModel;
import com.evefarm.util.DateUtil;
import com.evefarm.util.IskFormatter;

import java.util.List;

public final class SpawnTableModel extends ColumnTableModel<SpawnMember> {

    private static final List<ColumnDef<SpawnMember>> COLUMNS = List.of(
            new ColumnDef<>("npc", "NPC", String.class, r -> r.npc().name()),
            new ColumnDef<>("spawnClass", "Kind", String.class, r -> r.spawnClass().toString()),
            new ColumnDef<>("group", "Group", String.class, r -> OfficersTableModel.shortGroupName(r.groupName())),
            new ColumnDef<>("kills", "Killed", Integer.class, r -> r.npc().kills()),
            new ColumnDef<>("bounty", "Bounty", String.class,
                    r -> r.npc().bounty() > 0 ? IskFormatter.format(r.npc().bounty()) : ""),
            new ColumnDef<>("firstSeen", "First Seen (EVE)", String.class,
                    r -> DateUtil.formatEveClock(r.npc().firstSeenAt())),
            new ColumnDef<>("lastKill", "Last Kill (EVE)", String.class,
                    r -> DateUtil.formatEveClock(r.npc().lastKillAt())),
            new ColumnDef<>("dealt", "Damage Dealt", Long.class, r -> r.npc().damageDealt()),
            new ColumnDef<>("taken", "Damage Taken", Long.class, r -> r.npc().damageTaken())
    );

    public SpawnTableModel() {
        super(COLUMNS);
    }
}
