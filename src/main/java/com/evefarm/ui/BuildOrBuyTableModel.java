package com.evefarm.ui;

import com.evefarm.ui.column.ColumnDef;
import com.evefarm.ui.column.ColumnTableModel;
import com.evefarm.util.IskFormatter;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

public final class BuildOrBuyTableModel extends ColumnTableModel<BuildOrBuyTableModel.Row> {

    private static final String BUILD = "build";

    public record Row(int typeId, String name, boolean reaction, long needed, int runs, double marketPrice,
                      double buildPrice, double saving, boolean built, Duration time) {
    }

    private static final List<ColumnDef<Row>> COLUMNS = List.of(
            new ColumnDef<>(BUILD, "Build", Boolean.class, Row::built),
            new ColumnDef<>("item", "Item", String.class, Row::name),
            new ColumnDef<>("kind", "Made by", String.class, r -> r.reaction() ? "Reaction" : "Manufacturing"),
            new ColumnDef<>("quantity", "Quantity", String.class, r -> String.format(Locale.US, "%,d", r.needed())),
            new ColumnDef<>("runs", "Runs", Integer.class, Row::runs),
            new ColumnDef<>("market", "Market Price", String.class, r -> IskFormatter.format(r.marketPrice())),
            new ColumnDef<>("buildCost", "Build Cost", String.class, r -> IskFormatter.format(r.buildPrice())),
            new ColumnDef<>("saving", "Saving", String.class, r -> IskFormatter.format(r.saving())),
            new ColumnDef<>("time", "Job Time", String.class, r -> PlanTableModel.formatDuration(r.time())),
            new ColumnDef<>("typeId", "Type ID", Integer.class, false, Row::typeId)
    );

    private final BiConsumer<Integer, Boolean> onChoice;

    public BuildOrBuyTableModel(BiConsumer<Integer, Boolean> onChoice) {
        super(COLUMNS);
        this.onChoice = onChoice;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return BUILD.equals(columns().get(columnIndex).key());
    }

    @Override
    public void setValueAt(Object value, int rowIndex, int columnIndex) {
        if (isCellEditable(rowIndex, columnIndex) && value instanceof Boolean build) {
            onChoice.accept(rowAt(rowIndex).typeId(), build);
        }
    }
}
