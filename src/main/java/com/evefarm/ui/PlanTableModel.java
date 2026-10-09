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
            new ColumnDef<>("position", "#", Integer.class, PlanRow::position,
                    "The training order"),
            new ColumnDef<>("skill", "Skill", String.class,
                    r -> r.skillName() + " " + SkillPlanText.roman(r.entry().level()),
                    "The skill and the level to train"),
            new ColumnDef<>("type", "Type", String.class, r -> r.entry().planned() ? "Planned" : "Prerequisite",
                    "Planned when you added it, Prerequisite when a planned skill needs it"),
            new ColumnDef<>("time", "Training Time", String.class, r -> formatDuration(r.time()),
                    "How long the level takes to train, with the character's attributes"),
            new ColumnDef<>("start", "Start", String.class, false, r -> formatDate(r.start()),
                    "When the level starts training, in your local time"),
            new ColumnDef<>("finish", "Finish", String.class, r -> formatDate(r.finish()),
                    "When the level finishes training, in your local time"),
            new ColumnDef<>("done", "Done", String.class, r -> formatPercent(r.percentDone()),
                    "How much of the level is already trained"),
            new ColumnDef<>("sp", "SP", String.class, r -> String.format(Locale.US, "%,d", r.spNeeded()),
                    "The skill points the level still needs"),
            new ColumnDef<>("spTotal", "Total SP After", String.class, false,
                    r -> String.format(Locale.US, "%,d", r.spTotalAfter()),
                    "The character's total skill points once the level is trained"),
            new ColumnDef<>("rank", "Rank", Integer.class, false, PlanRow::rank,
                    "The skill's rank: how many times longer it takes than a rank 1 skill"),
            new ColumnDef<>("attributes", "Attributes", String.class, PlanTableModel::attributes,
                    "The primary / secondary attributes that speed up the training"),
            new ColumnDef<>("group", "Group", String.class, r -> nullToEmpty(r.groupName()),
                    "The skill's group"),
            new ColumnDef<>("notes", "Notes", String.class, r -> nullToEmpty(r.entry().notes()),
                    "Your notes on the level")
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
