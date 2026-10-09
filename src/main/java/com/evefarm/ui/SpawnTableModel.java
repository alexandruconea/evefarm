package com.evefarm.ui;

import com.evefarm.model.SpawnMember;
import com.evefarm.ui.column.ColumnDef;
import com.evefarm.ui.column.ColumnTableModel;
import com.evefarm.util.DateUtil;
import com.evefarm.util.IskFormatter;

import java.util.List;

public final class SpawnTableModel extends ColumnTableModel<SpawnMember> {

    private static final List<ColumnDef<SpawnMember>> COLUMNS = List.of(
            new ColumnDef<>("npc", "NPC", String.class, r -> r.npc().name(),
                    "The NPC's name"),
            new ColumnDef<>("spawnClass", "Kind", String.class, r -> r.spawnClass().toString(),
                    "Officer, Commander, Belt rat or Other"),
            new ColumnDef<>("group", "Group", String.class, r -> OfficersTableModel.shortGroupName(r.groupName()),
                    "The NPC's group"),
            new ColumnDef<>("kills", "Killed", Integer.class, r -> r.npc().kills(),
                    "How many your characters killed"),
            new ColumnDef<>("bounty", "Bounty", String.class,
                    r -> r.npc().bounty() > 0 ? IskFormatter.format(r.npc().bounty()) : "",
                    "The bounty of those killed"),
            new ColumnDef<>("firstSeen", "First Seen (EVE)", String.class,
                    r -> DateUtil.formatEveClock(r.npc().firstSeenAt()),
                    "When your characters first met it, in EVE time"),
            new ColumnDef<>("lastKill", "Last Kill (EVE)", String.class,
                    r -> DateUtil.formatEveClock(r.npc().lastKillAt()),
                    "When the last one was killed, in EVE time"),
            new ColumnDef<>("dealt", "Damage Dealt", Long.class, r -> r.npc().damageDealt(),
                    "The damage your characters dealt to it"),
            new ColumnDef<>("taken", "Damage Taken", Long.class, r -> r.npc().damageTaken(),
                    "The damage your characters took from it")
    );

    public SpawnTableModel() {
        super(COLUMNS);
    }
}
