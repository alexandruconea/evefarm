package com.evefarm.ui;

import com.evefarm.model.IndustryJobRow;
import com.evefarm.ui.column.ColumnDef;
import com.evefarm.ui.column.ColumnTableModel;
import com.evefarm.util.DateUtil;
import com.evefarm.util.IskFormatter;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

public final class IndustryJobsTableModel extends ColumnTableModel<IndustryJobRow> {

    private static final Map<Integer, String> ACTIVITY_NAMES = Map.of(
            1, "Manufacturing",
            3, "Time Efficiency Research",
            4, "Material Efficiency Research",
            5, "Copying",
            7, "Reverse Engineering",
            8, "Invention",
            9, "Reactions"
    );

    private static final List<ColumnDef<IndustryJobRow>> COLUMNS = List.of(
            new ColumnDef<>("character", "Character", String.class, IndustryJobRow::characterName,
                    "The character who started the job"),
            new ColumnDef<>("activity", "Activity", String.class,
                    r -> ACTIVITY_NAMES.getOrDefault(r.activityId(), "Activity #" + r.activityId()),
                    "Manufacturing, research, copying, invention or reactions"),
            new ColumnDef<>("status", "Status", String.class, r -> statusText(r, Instant.now()),
                    "Where the job stands. Ready means it has finished and waits to be delivered"),
            new ColumnDef<>("blueprint", "Blueprint", String.class, IndustryJobRow::blueprintName,
                    "The blueprint the job uses"),
            new ColumnDef<>("product", "Product", String.class, r -> r.productName() == null ? "" : r.productName(),
                    "What the job makes"),
            new ColumnDef<>("runs", "Runs", String.class, r -> r.runs() == null ? "" : String.valueOf(r.runs()),
                    "How many runs the job has"),
            new ColumnDef<>("cost", "Cost", String.class, r -> r.cost() == null ? "" : IskFormatter.format(r.cost()),
                    "What starting the job cost: the installation fee and the facility tax"),
            new ColumnDef<>("facility", "Facility", String.class, r -> r.facilityName() == null ? "" : r.facilityName(),
                    "The station or structure the job runs in"),
            new ColumnDef<>("startDate", "Start Date", String.class, r -> DateUtil.formatIsoInstant(r.startDate()),
                    "When the job started, in your local time"),
            new ColumnDef<>("endDate", "End Date", String.class, r -> DateUtil.formatIsoInstant(r.endDate()),
                    "When the job ends, in your local time"),
            new ColumnDef<>("jobId", "Job ID", Long.class, false, IndustryJobRow::jobId,
                    "EVE's ID of the job"),
            new ColumnDef<>("characterId", "Character ID", Long.class, false, IndustryJobRow::characterId,
                    "EVE's ID of the character")
    );

    public IndustryJobsTableModel() {
        super(COLUMNS);
    }

    static String statusText(IndustryJobRow row, Instant now) {
        if (row.status() == null) {
            return "";
        }
        if ("active".equals(row.status()) && isPast(row.endDate(), now)) {
            return "Ready";
        }
        return capitalize(row.status());
    }

    private static boolean isPast(String isoInstant, Instant now) {
        try {
            return isoInstant != null && !Instant.parse(isoInstant).isAfter(now);
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private static String capitalize(String value) {
        return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
