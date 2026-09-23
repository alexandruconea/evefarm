package com.evefarm.ui;

import com.evefarm.model.AgentRow;
import com.evefarm.ui.column.ColumnDef;
import com.evefarm.ui.column.ColumnTableModel;

import java.util.List;
import java.util.Locale;

public final class AgentsTableModel extends ColumnTableModel<AgentRow> {

    private static final List<ColumnDef<AgentRow>> COLUMNS = List.of(
            new ColumnDef<>("agent", "Agent", String.class, AgentRow::agentName),
            new ColumnDef<>("corporation", "Corporation", String.class, r -> nullToEmpty(r.corporationName())),
            new ColumnDef<>("faction", "Faction", String.class, r -> nullToEmpty(r.factionName())),
            new ColumnDef<>("level", "Level", Integer.class, AgentRow::level),
            new ColumnDef<>("station", "Station", String.class, r -> nullToEmpty(r.stationName())),
            new ColumnDef<>("system", "System", String.class, r -> nullToEmpty(r.solarSystemName())),
            new ColumnDef<>("security", "Security", String.class, r -> formatSecurity(r.security())),
            new ColumnDef<>("constellation", "Constellation", String.class, r -> nullToEmpty(r.constellationName())),
            new ColumnDef<>("region", "Region", String.class, r -> nullToEmpty(r.regionName())),
            new ColumnDef<>("division", "Division", String.class, r -> nullToEmpty(r.divisionName())),
            new ColumnDef<>("type", "Type", String.class, r -> nullToEmpty(r.agentTypeName())),
            new ColumnDef<>("locator", "Locator", String.class, r -> r.isLocator() ? "Yes" : "No"),
            new ColumnDef<>("agentId", "Agent ID", Long.class, false, AgentRow::agentId),
            new ColumnDef<>("corporationId", "Corporation ID", Long.class, false, AgentRow::corporationId),
            new ColumnDef<>("factionId", "Faction ID", Long.class, false, AgentRow::factionId)
    );

    public AgentsTableModel() {
        super(COLUMNS);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String formatSecurity(Double value) {
        return value == null ? "" : String.format(Locale.US, "%.1f", value);
    }
}
