package com.evefarm.ui;

import com.evefarm.model.StandingRow;
import com.evefarm.service.StandingService;
import com.evefarm.ui.column.ColumnDef;
import com.evefarm.ui.column.ColumnTableModel;

import java.util.List;
import java.util.Locale;

public final class StandingsTableModel extends ColumnTableModel<StandingRow> {

    private static final List<ColumnDef<StandingRow>> FACTION_COLUMNS = List.of(
            new ColumnDef<>("character", "Character", String.class, StandingRow::characterName),
            new ColumnDef<>("name", "Name", String.class, StandingRow::name),
            new ColumnDef<>("kind", "Type", String.class, r -> StandingService.kind(r.fromType())),
            new ColumnDef<>("standing", "Standing", String.class, r -> formatStanding(r.standing())),
            new ColumnDef<>("faction", "Faction", String.class, false, r -> nullToEmpty(r.factionName())),
            new ColumnDef<>("fromId", "From ID", Long.class, false, StandingRow::fromId),
            new ColumnDef<>("characterId", "Character ID", Long.class, false, StandingRow::characterId)
    );

    private static final List<ColumnDef<StandingRow>> AGENT_COLUMNS = List.of(
            new ColumnDef<>("character", "Character", String.class, StandingRow::characterName),
            new ColumnDef<>("name", "Agent", String.class, StandingRow::name),
            new ColumnDef<>("standing", "Standing", String.class, r -> formatStanding(r.standing())),
            new ColumnDef<>("corporation", "Corporation", String.class, r -> nullToEmpty(r.corporationName())),
            new ColumnDef<>("faction", "Faction", String.class, false, r -> nullToEmpty(r.factionName())),
            new ColumnDef<>("level", "Level", Integer.class, StandingRow::level),
            new ColumnDef<>("division", "Division", String.class, r -> nullToEmpty(r.divisionName())),
            new ColumnDef<>("system", "System", String.class, r -> nullToEmpty(r.systemName())),
            new ColumnDef<>("fromId", "Agent ID", Long.class, false, StandingRow::fromId),
            new ColumnDef<>("characterId", "Character ID", Long.class, false, StandingRow::characterId)
    );

    private StandingsTableModel(List<ColumnDef<StandingRow>> columns) {
        super(columns);
    }

    static StandingsTableModel factionsAndCorporations() {
        return new StandingsTableModel(FACTION_COLUMNS);
    }

    static StandingsTableModel agents() {
        return new StandingsTableModel(AGENT_COLUMNS);
    }

    static String formatStanding(double standing) {
        return String.format(Locale.US, "%.2f", standing);
    }
}
