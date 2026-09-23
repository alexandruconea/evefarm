package com.evefarm.ui;

import com.evefarm.model.IndustryJobRow;
import com.evefarm.ui.column.ColumnDef;
import com.evefarm.ui.column.ColumnTableModel;
import com.evefarm.util.DateUtil;
import com.evefarm.util.IskFormatter;

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
            new ColumnDef<>("character", "Character", String.class, IndustryJobRow::characterName),
            new ColumnDef<>("activity", "Activity", String.class,
                    r -> ACTIVITY_NAMES.getOrDefault(r.activityId(), "Activity #" + r.activityId())),
            new ColumnDef<>("status", "Status", String.class, r -> r.status() == null ? "" : capitalize(r.status())),
            new ColumnDef<>("blueprint", "Blueprint", String.class, IndustryJobRow::blueprintName),
            new ColumnDef<>("product", "Product", String.class, r -> r.productName() == null ? "" : r.productName()),
            new ColumnDef<>("runs", "Runs", String.class, r -> r.runs() == null ? "" : String.valueOf(r.runs())),
            new ColumnDef<>("cost", "Cost", String.class, r -> r.cost() == null ? "" : IskFormatter.format(r.cost())),
            new ColumnDef<>("facility", "Facility", String.class, r -> r.facilityName() == null ? "" : r.facilityName()),
            new ColumnDef<>("startDate", "Start Date", String.class, r -> DateUtil.formatIsoInstant(r.startDate())),
            new ColumnDef<>("endDate", "End Date", String.class, r -> DateUtil.formatIsoInstant(r.endDate())),
            new ColumnDef<>("jobId", "Job ID", Long.class, false, IndustryJobRow::jobId),
            new ColumnDef<>("characterId", "Character ID", Long.class, false, IndustryJobRow::characterId)
    );

    public IndustryJobsTableModel() {
        super(COLUMNS);
    }

    private static String capitalize(String value) {
        return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
