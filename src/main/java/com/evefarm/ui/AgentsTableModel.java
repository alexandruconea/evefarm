package com.evefarm.ui;

import com.evefarm.model.AgentRow;
import com.evefarm.ui.column.ColumnDef;
import com.evefarm.ui.column.ColumnTableModel;

import java.util.List;
import java.util.Locale;

public final class AgentsTableModel extends ColumnTableModel<AgentRow> {

    private static final List<ColumnDef<AgentRow>> COLUMNS = List.of(
            new ColumnDef<>("agent", "Agent", String.class, AgentRow::agentName,
                    "The agent's name"),
            new ColumnDef<>("corporation", "Corporation", String.class, r -> nullToEmpty(r.corporationName()),
                    "The NPC corporation the agent works for"),
            new ColumnDef<>("faction", "Faction", String.class, r -> nullToEmpty(r.factionName()),
                    "The faction of the agent's corporation"),
            new ColumnDef<>("level", "Level", Integer.class, AgentRow::level,
                    "The agent's level, 1 to 5: higher levels give harder missions and bigger rewards"),
            new ColumnDef<>("station", "Station", String.class, r -> nullToEmpty(r.stationName()),
                    "The station the agent is in"),
            new ColumnDef<>("system", "System", ZkillboardLink.class,
                    r -> ZkillboardLink.system(r.solarSystemName(), r.solarSystemId()),
                    "The agent's solar system. Click the icon to see the recent kills there on zKillboard"),
            new ColumnDef<>("security", "Security", String.class, r -> formatSecurity(r.security()),
                    "The security status of the agent's system"),
            new ColumnDef<>("constellation", "Constellation", ZkillboardLink.class,
                    r -> ZkillboardLink.constellation(r.constellationName(), r.constellationId()),
                    "The agent's constellation. Click the icon to see the recent kills there on zKillboard"),
            new ColumnDef<>("region", "Region", ZkillboardLink.class,
                    r -> ZkillboardLink.region(r.regionName(), r.regionId()),
                    "The agent's region. Click the icon to see the recent kills there on zKillboard"),
            new ColumnDef<>("division", "Division", String.class, r -> nullToEmpty(r.divisionName()),
                    "The kind of work the agent gives, such as Security, Distribution or Mining"),
            new ColumnDef<>("type", "Type", String.class, r -> nullToEmpty(r.agentTypeName()),
                    "The kind of agent, such as a basic, research or storyline agent"),
            new ColumnDef<>("locator", "Locator", String.class, r -> r.isLocator() ? "Yes" : "No",
                    "Yes when the agent can find other characters for you"),
            new ColumnDef<>("agentId", "Agent ID", Long.class, false, AgentRow::agentId,
                    "EVE's ID of the agent"),
            new ColumnDef<>("corporationId", "Corporation ID", Long.class, false, AgentRow::corporationId,
                    "EVE's ID of the corporation"),
            new ColumnDef<>("factionId", "Faction ID", Long.class, false, AgentRow::factionId,
                    "EVE's ID of the faction")
    );

    public AgentsTableModel() {
        super(COLUMNS);
    }

    private static String formatSecurity(Double value) {
        return value == null ? "" : String.format(Locale.US, "%.1f", value);
    }
}
