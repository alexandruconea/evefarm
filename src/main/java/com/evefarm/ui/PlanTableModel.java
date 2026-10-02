package com.evefarm.ui;

import com.evefarm.model.PlanRow;
import com.evefarm.service.SkillPlanText;
import com.evefarm.ui.column.ColumnDef;
import com.evefarm.ui.column.ColumnTableModel;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public final class PlanTableModel extends ColumnTableModel<PlanRow> {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private static final List<ColumnDef<PlanRow>> COLUMNS = List.of(
            new ColumnDef<>("position", "#", Integer.class, PlanRow::position),
            new ColumnDef<>("skill", "Skill", String.class,
                    r -> r.skillName() + " " + SkillPlanText.roman(r.entry().level())),
            new ColumnDef<>("type", "Type", String.class, r -> r.entry().planned() ? "Planned" : "Prerequisite"),
            new ColumnDef<>("time", "Training Time", String.class, r -> formatDuration(r.time())),
            new ColumnDef<>("start", "Start", String.class, false, r -> formatDate(r.start())),
            new ColumnDef<>("finish", "Finish", String.class, r -> formatDate(r.finish())),
            new ColumnDef<>("done", "Done", String.class, r -> formatPercent(r.percentDone())),
            new ColumnDef<>("sp", "SP", String.class, r -> String.format(Locale.US, "%,d", r.spNeeded())),
            new ColumnDef<>("spTotal", "Total SP After", String.class, false,
                    r -> String.format(Locale.US, "%,d", r.spTotalAfter())),
            new ColumnDef<>("rank", "Rank", Integer.class, false, PlanRow::rank),
            new ColumnDef<>("attributes", "Attributes", String.class, PlanTableModel::attributes),
            new ColumnDef<>("group", "Group", String.class, r -> nullToEmpty(r.groupName())),
            new ColumnDef<>("notes", "Notes", String.class, r -> nullToEmpty(r.entry().notes()))
    );

    public PlanTableModel() {
        super(COLUMNS);
    }

    static String formatDuration(Duration duration) {
        long seconds = Math.max(0, duration.getSeconds());
        long days = seconds / 86_400;
        long hours = seconds % 86_400 / 3_600;
        long minutes = seconds % 3_600 / 60;
        if (days > 0) {
            return days + "d " + hours + "h " + minutes + "m";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m " + seconds % 60 + "s";
        }
        return seconds + "s";
    }

    static String formatDate(Instant instant) {
        return DATE_TIME.format(instant);
    }

    private static String formatPercent(double percent) {
        return percent <= 0 ? "" : String.format(Locale.US, "%.0f%%", percent * 100);
    }

    private static String attributes(PlanRow row) {
        if (row.primaryAttribute() == null || row.primaryAttribute().isEmpty()) {
            return "";
        }
        return row.primaryAttribute() + " / " + row.secondaryAttribute();
    }
}
