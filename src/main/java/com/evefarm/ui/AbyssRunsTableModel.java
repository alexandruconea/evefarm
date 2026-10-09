package com.evefarm.ui;

import com.evefarm.model.AbyssalRun;
import com.evefarm.ui.column.ColumnDef;
import com.evefarm.ui.column.ColumnTableModel;
import com.evefarm.util.DateUtil;
import com.evefarm.util.IskFormatter;

import java.util.List;
import java.util.Locale;

public final class AbyssRunsTableModel extends ColumnTableModel<AbyssalRun> {

    static final String RESULT_COLUMN = "result";
    static final String SURVIVED = "Survived";

    private static final List<ColumnDef<AbyssalRun>> COLUMNS = List.of(
            new ColumnDef<>("started", "Started", String.class, r -> DateUtil.format(r.startedAt()),
                    "When the run started, in your local time"),
            new ColumnDef<>("character", "Character", String.class, AbyssalRun::characterName,
                    "The character who ran the filament"),
            new ColumnDef<>("tier", "Tier", String.class, r -> r.tier() == null ? "" : r.tier().toString(),
                    "The filament tier, from Tranquil (T0) to Cataclysmic (T6)"),
            new ColumnDef<>("weather", "Weather", String.class, r -> r.weather() == null ? "" : r.weather().toString(),
                    "The filament weather: Dark, Electrical, Exotic, Firestorm or Gamma"),
            new ColumnDef<>("fleet", "Fleet", String.class, r -> r.fleet() == null ? "" : r.fleet().toString(),
                    "The ships that went in: 1 Cruiser, 2 Destroyers or 3 Frigates"),
            new ColumnDef<>("ship", "Ship", String.class, r -> nullToEmpty(r.shipName()),
                    "The ship the character flew"),
            new ColumnDef<>("time", "Time", String.class, r -> formatDuration(r.durationSeconds()),
                    "How long the run took, from entering the Abyss to leaving it"),
            new ColumnDef<>(RESULT_COLUMN, "Result", String.class, r -> r.survived() ? SURVIVED : "Lost",
                    "Survived, or Lost when the character came back in a capsule"),
            new ColumnDef<>("loot", "Loot", String.class, r -> IskFormatter.format(r.lootValue()),
                    "What the loot is worth: the cargo after the run less the cargo before it"),
            new ColumnDef<>("filament", "Filament", String.class, r -> formatIsk(r.filamentCost()),
                    "What the filaments cost: one for each ship in the fleet, at the market price"),
            new ColumnDef<>("profit", "Profit", String.class, r -> IskFormatter.format(r.profit()),
                    "The loot less the filament cost"),
            new ColumnDef<>("iskPerHour", "ISK/h", String.class, r -> formatIsk(r.iskPerHour()),
                    "The profit per hour of run time"),
            new ColumnDef<>("notes", "Notes", String.class, r -> nullToEmpty(r.notes()),
                    "Your notes on the run"),
            new ColumnDef<>("runId", "Run ID", Long.class, false, AbyssalRun::id,
                    "EVE Farm's number for the run"),
            new ColumnDef<>("characterId", "Character ID", Long.class, false, AbyssalRun::characterId,
                    "EVE's ID of the character")
    );

    public AbyssRunsTableModel() {
        super(COLUMNS);
    }

    static String formatDuration(Integer seconds) {
        if (seconds == null) {
            return "";
        }
        return formatDuration((long) seconds);
    }

    static String formatDuration(long seconds) {
        long hours = seconds / 3600;
        long minutes = seconds % 3600 / 60;
        long rest = seconds % 60;
        return hours > 0
                ? String.format(Locale.US, "%d:%02d:%02d", hours, minutes, rest)
                : String.format(Locale.US, "%02d:%02d", minutes, rest);
    }
}
