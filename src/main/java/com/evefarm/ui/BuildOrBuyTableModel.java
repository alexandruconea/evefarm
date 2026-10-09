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

    public record Row(int typeId, String name, boolean reaction, long needed, long inStock, int runs, long surplus,
                      double marketPrice, double buildPrice, boolean built, Duration time) {

        private boolean onMarket() {
            return marketPrice > 0;
        }

        private String moreProfitable() {
            if (!onMarket()) {
                return "Build (not on the market)";
            }
            return buildPrice < marketPrice ? "Build" : "Buy";
        }

        private String saving() {
            return onMarket() ? IskFormatter.format(Math.abs(marketPrice - buildPrice) * needed) : "";
        }
    }

    private static final List<ColumnDef<Row>> COLUMNS = List.of(
            new ColumnDef<>(BUILD, "Build", Boolean.class, Row::built,
                    "Tick to build the item yourself, clear to buy it"),
            new ColumnDef<>("best", "More Profitable", String.class, Row::moreProfitable,
                    "Whether building or buying the item costs you less"),
            new ColumnDef<>("item", "Item", String.class, Row::name,
                    "An item the build needs that you could make yourself"),
            new ColumnDef<>("kind", "Made by", String.class, r -> r.reaction() ? "Reaction" : "Manufacturing",
                    "Whether a reaction or a manufacturing job makes it"),
            new ColumnDef<>("quantity", "Quantity", String.class, r -> String.format(Locale.US, "%,d", r.needed()),
                    "How many the whole build needs"),
            new ColumnDef<>("inStock", "In Stock", String.class,
                    r -> r.inStock() > 0 ? String.format(Locale.US, "%,d", r.inStock()) : "",
                    "How many come from your assets (see the Stock setting)"),
            new ColumnDef<>("runs", "Runs", Integer.class, Row::runs,
                    "How many runs the job needs"),
            new ColumnDef<>("surplus", "Surplus", String.class,
                    r -> r.surplus() > 0 ? String.format(Locale.US, "%,d", r.surplus()) : "",
                    "What the job makes beyond what you need, as it makes whole runs"),
            new ColumnDef<>("market", "Market Price", String.class, r -> IskFormatter.format(r.marketPrice()),
                    "The price of one unit on the market"),
            new ColumnDef<>("buildCost", "Build Cost", String.class, r -> IskFormatter.format(r.buildPrice()),
                    "What one unit costs you to make: its materials and the job fee"),
            new ColumnDef<>("saving", "Saving", String.class, Row::saving,
                    "How much you save on the whole quantity by taking the cheaper way"),
            new ColumnDef<>("time", "Job Time", String.class, r -> PlanTableModel.formatDuration(r.time()),
                    "How long the job takes"),
            new ColumnDef<>("typeId", "Type ID", Integer.class, false, Row::typeId,
                    "EVE's ID of the item type")
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
