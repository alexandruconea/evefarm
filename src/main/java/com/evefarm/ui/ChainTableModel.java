package com.evefarm.ui;

import com.evefarm.ui.column.ColumnDef;
import com.evefarm.ui.column.ColumnTableModel;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

public final class ChainTableModel extends ColumnTableModel<ChainTableModel.Row> {

    public record Row(String step, int typeId, String item, String madeBy, int runs, long makes, long needed,
                      long surplus, Duration time, String uses) {
    }

    private static final List<ColumnDef<Row>> COLUMNS = List.of(
            new ColumnDef<>("step", "Step", String.class, Row::step),
            new ColumnDef<>("item", "Item", String.class, Row::item),
            new ColumnDef<>("madeBy", "Made by", String.class, Row::madeBy),
            new ColumnDef<>("runs", "Runs", String.class, r -> r.runs() > 0 ? count(r.runs()) : ""),
            new ColumnDef<>("makes", "Makes", String.class, r -> count(r.makes())),
            new ColumnDef<>("needed", "Needed", String.class, r -> count(r.needed())),
            new ColumnDef<>("surplus", "Surplus", String.class, r -> r.surplus() > 0 ? count(r.surplus()) : ""),
            new ColumnDef<>("time", "Job Time", String.class, r -> PlanTableModel.formatDuration(r.time())),
            new ColumnDef<>("uses", "Uses", String.class, Row::uses),
            new ColumnDef<>("typeId", "Type ID", Integer.class, false, Row::typeId)
    );

    public ChainTableModel() {
        super(COLUMNS);
    }

    private static String count(long value) {
        return String.format(Locale.US, "%,d", value);
    }
}
