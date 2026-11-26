package com.evefarm.ui;

import com.evefarm.model.MissionAgent;
import com.evefarm.service.MissionIncome.AgentTotals;
import com.evefarm.ui.EveEntity.Kind;
import com.evefarm.ui.column.ColumnDef;
import com.evefarm.ui.column.ColumnTableModel;
import com.evefarm.ui.column.Isk;

import java.util.List;
import java.util.stream.Stream;

public final class MissionAgentsTableModel extends ColumnTableModel<MissionAgentsTableModel.Row> {

    static final String NO_TURN_IN = "No turn-in nearby";

    record Row(AgentTotals totals, MissionAgent agent) {

        EveEntity agentLink() {
            return new EveEntity(MissionAgentsTableModel.agent(totals), Kind.AGENT,
                    agent == null ? null : agent.agentId());
        }

        EveEntity corporationLink() {
            return agent == null ? new EveEntity(null, Kind.CORPORATION, null)
                    : new EveEntity(agent.corporationName(), Kind.CORPORATION, agent.corporationId());
        }

        EveEntity factionLink() {
            return agent == null ? new EveEntity(null, Kind.FACTION, null)
                    : new EveEntity(agent.factionName(), Kind.FACTION, agent.factionId());
        }

        Stream<EveEntity> links() {
            return Stream.of(agentLink(), corporationLink(), factionLink());
        }
    }

    private static final List<ColumnDef<Row>> COLUMNS = List.of(
            new ColumnDef<>("agent", "Agent", EveEntity.class, Row::agentLink,
                    "The agent who gave the missions. Bounties count for the agent of the turn-in closest to them. "
                            + "Click to see the agent in Standings"),
            new ColumnDef<>("corporation", "Corporation", EveEntity.class, Row::corporationLink,
                    "The NPC corporation the agent works for. Click to see it in Standings"),
            new ColumnDef<>("faction", "Faction", EveEntity.class, Row::factionLink,
                    "The faction of the agent's corporation. Click to see it in Standings"),
            new ColumnDef<>("missions", "Missions", Integer.class, r -> r.totals().missions(),
                    "How many missions were turned in"),
            new ColumnDef<>("rewards", "Rewards", Isk.class, false,
                    r -> Isk.of(r.totals().rewards()),
                    "The ISK reward of the missions"),
            new ColumnDef<>("timeBonuses", "Time Bonuses", Isk.class, false,
                    r -> Isk.of(r.totals().timeBonuses()),
                    "The bonus for finishing missions in time"),
            new ColumnDef<>("bounties", "Bounties", Isk.class, false,
                    r -> Isk.of(r.totals().bounties()),
                    "Bounties paid close to this agent's turn-ins"),
            new ColumnDef<>("isk", "Mission ISK", Isk.class, r -> Isk.of(r.totals().isk()),
                    "Rewards, time bonuses and bounties together"),
            new ColumnDef<>("perMission", "Per Mission", Isk.class,
                    r -> r.totals().missions() == 0 ? Isk.NONE : Isk.of(r.totals().perMission()),
                    "Mission ISK divided by the missions")
    );

    public MissionAgentsTableModel() {
        super(COLUMNS);
    }

    static String agent(AgentTotals row) {
        return row.agentName() == null ? NO_TURN_IN : row.agentName();
    }
}
