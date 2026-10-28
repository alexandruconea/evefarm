package com.evefarm.ui;

import com.evefarm.service.MissionIncome.AgentTotals;
import com.evefarm.ui.column.ColumnDef;
import com.evefarm.ui.column.ColumnTableModel;
import com.evefarm.util.IskFormatter;

import java.util.List;

public final class MissionAgentsTableModel extends ColumnTableModel<AgentTotals> {

    static final String NO_TURN_IN = "No turn-in nearby";

    private static final List<ColumnDef<AgentTotals>> COLUMNS = List.of(
            new ColumnDef<>("agent", "Agent", String.class, MissionAgentsTableModel::agent,
                    "The agent who gave the missions. Bounties count for the agent of the turn-in closest to them"),
            new ColumnDef<>("missions", "Missions", Integer.class, AgentTotals::missions,
                    "How many missions were turned in"),
            new ColumnDef<>("rewards", "Rewards", String.class, false, r -> IskFormatter.format(r.rewards()),
                    "The ISK reward of the missions"),
            new ColumnDef<>("timeBonuses", "Time Bonuses", String.class, false,
                    r -> IskFormatter.format(r.timeBonuses()),
                    "The bonus for finishing missions in time"),
            new ColumnDef<>("bounties", "Bounties", String.class, false, r -> IskFormatter.format(r.bounties()),
                    "Bounties paid close to this agent's turn-ins"),
            new ColumnDef<>("isk", "Mission ISK", String.class, r -> IskFormatter.format(r.isk()),
                    "Rewards, time bonuses and bounties together"),
            new ColumnDef<>("perMission", "Per Mission", String.class,
                    r -> r.missions() == 0 ? "" : IskFormatter.format(r.perMission()),
                    "Mission ISK divided by the missions")
    );

    public MissionAgentsTableModel() {
        super(COLUMNS);
    }

    static String agent(AgentTotals row) {
        return row.agentName() == null ? NO_TURN_IN : row.agentName();
    }
}
