package com.evefarm.ui;

import com.evefarm.model.StandingRow;
import com.evefarm.service.StandingService;
import com.evefarm.ui.column.ColumnDef;
import com.evefarm.ui.column.ColumnTableModel;

import java.util.List;
import java.util.Locale;

public final class StandingsTableModel extends ColumnTableModel<StandingRow> {

    private static final List<ColumnDef<StandingRow>> FACTION_COLUMNS = List.of(
            new ColumnDef<>("character", "Character", String.class, StandingRow::characterName,
                    "The character whose standing it is"),
            new ColumnDef<>("name", "Name", String.class, StandingRow::name,
                    "The faction or NPC corporation"),
            new ColumnDef<>("kind", "Type", String.class, r -> StandingService.kind(r.fromType()),
                    "Faction or Corporation"),
            new ColumnDef<>("standing", "Standing", String.class, r -> formatStanding(r.standing()),
                    "The character's standing, from -10 to +10, without the bonus from social skills"),
            new ColumnDef<>("faction", "Faction", String.class, false, r -> nullToEmpty(r.factionName()),
                    "The faction of the corporation"),
            new ColumnDef<>("fromId", "From ID", Long.class, false, StandingRow::fromId,
                    "EVE's ID of the faction or corporation"),
            new ColumnDef<>("characterId", "Character ID", Long.class, false, StandingRow::characterId,
                    "EVE's ID of the character")
    );

    private static final List<ColumnDef<StandingRow>> AGENT_COLUMNS = List.of(
            new ColumnDef<>("character", "Character", String.class, StandingRow::characterName,
                    "The character whose standing it is"),
            new ColumnDef<>("name", "Agent", String.class, StandingRow::name,
                    "The agent's name"),
            new ColumnDef<>("standing", "Standing", String.class, r -> formatStanding(r.standing()),
                    "The character's standing with the agent, from -10 to +10, without the bonus from social skills"),
            new ColumnDef<>("corporation", "Corporation", String.class, r -> nullToEmpty(r.corporationName()),
                    "The NPC corporation the agent works for"),
            new ColumnDef<>("faction", "Faction", String.class, false, r -> nullToEmpty(r.factionName()),
                    "The faction of the agent's corporation"),
            new ColumnDef<>("level", "Level", Integer.class, StandingRow::level,
                    "The agent's level, 1 to 5: higher levels give harder missions and bigger rewards"),
            new ColumnDef<>("division", "Division", String.class, r -> nullToEmpty(r.divisionName()),
                    "The kind of work the agent gives, such as Security, Distribution or Mining"),
            new ColumnDef<>("system", "System", String.class, r -> nullToEmpty(r.systemName()),
                    "The agent's solar system"),
            new ColumnDef<>("fromId", "Agent ID", Long.class, false, StandingRow::fromId,
                    "EVE's ID of the agent"),
            new ColumnDef<>("characterId", "Character ID", Long.class, false, StandingRow::characterId,
                    "EVE's ID of the character")
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
