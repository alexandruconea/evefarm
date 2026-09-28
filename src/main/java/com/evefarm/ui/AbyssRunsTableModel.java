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
            new ColumnDef<>("started", "Started", String.class, r -> DateUtil.format(r.startedAt())),
            new ColumnDef<>("character", "Character", String.class, AbyssalRun::characterName),
            new ColumnDef<>("tier", "Tier", String.class, r -> r.tier() == null ? "" : r.tier().toString()),
            new ColumnDef<>("weather", "Weather", String.class, r -> r.weather() == null ? "" : r.weather().toString()),
            new ColumnDef<>("ship", "Ship", String.class, r -> nullToEmpty(r.shipName())),
            new ColumnDef<>("time", "Time", String.class, r -> formatDuration(r.durationSeconds())),
            new ColumnDef<>(RESULT_COLUMN, "Result", String.class, r -> r.survived() ? SURVIVED : "Lost"),
            new ColumnDef<>("loot", "Loot", String.class, r -> IskFormatter.format(r.lootValue())),
            new ColumnDef<>("filament", "Filament", String.class, r -> formatIsk(r.filamentCost())),
            new ColumnDef<>("profit", "Profit", String.class, r -> IskFormatter.format(r.profit())),
            new ColumnDef<>("iskPerHour", "ISK/h", String.class, r -> formatIsk(r.iskPerHour())),
            new ColumnDef<>("notes", "Notes", String.class, r -> nullToEmpty(r.notes())),
            new ColumnDef<>("runId", "Run ID", Long.class, false, AbyssalRun::id),
            new ColumnDef<>("characterId", "Character ID", Long.class, false, AbyssalRun::characterId)
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

    private static String formatIsk(Double value) {
        return value == null ? "" : IskFormatter.format(value);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
