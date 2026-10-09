package com.evefarm.ui;

import com.evefarm.ui.column.ColumnDef;
import com.evefarm.ui.column.ColumnTableModel;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

public final class ChainTableModel extends ColumnTableModel<ChainTableModel.Row> {

    public record Row(String step, int typeId, String item, String madeBy, int runs, long makes, long needed,
                      long inStock, long surplus, Duration time, String uses) {
    }

    private static final List<ColumnDef<Row>> COLUMNS = List.of(
            new ColumnDef<>("step", "Step", String.class, Row::step,
                    "The step and how long it takes. The jobs of one step run side by side; run the steps in order"),
            new ColumnDef<>("item", "Item", String.class, Row::item,
                    "What the job makes"),
            new ColumnDef<>("madeBy", "Made by", String.class, Row::madeBy,
                    "Whether a reaction or a manufacturing job makes it"),
            new ColumnDef<>("runs", "Runs", String.class, r -> r.runs() > 0 ? count(r.runs()) : "",
                    "How many runs the job needs"),
            new ColumnDef<>("makes", "Makes", String.class, r -> count(r.makes()),
                    "How many units the job makes"),
            new ColumnDef<>("needed", "Needed", String.class, r -> count(r.needed()),
                    "How many units the chain needs"),
            new ColumnDef<>("inStock", "In Stock", String.class, r -> r.inStock() > 0 ? count(r.inStock()) : "",
                    "How many come from your assets (see the Stock setting)"),
            new ColumnDef<>("surplus", "Surplus", String.class, r -> r.surplus() > 0 ? count(r.surplus()) : "",
                    "What the job makes beyond what the chain needs"),
            new ColumnDef<>("time", "Job Time", String.class, r -> PlanTableModel.formatDuration(r.time()),
                    "How long the job takes"),
            new ColumnDef<>("uses", "Uses", String.class, Row::uses,
                    "The materials the job uses"),
            new ColumnDef<>("typeId", "Type ID", Integer.class, false, Row::typeId,
                    "EVE's ID of the item type")
    );

    public ChainTableModel() {
        super(COLUMNS);
    }

    private static String count(long value) {
        return String.format(Locale.US, "%,d", value);
    }
}
