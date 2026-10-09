package com.evefarm.ui;

import com.evefarm.ui.column.ColumnDef;
import com.evefarm.ui.column.ColumnTableModel;
import com.evefarm.util.IskFormatter;

import java.util.List;
import java.util.Locale;

public final class IndustryMaterialsTableModel extends ColumnTableModel<IndustryMaterialsTableModel.Row> {

    public record Row(int typeId, String name, long quantity, double unitPrice, double total, String source) {
    }

    private static final List<ColumnDef<Row>> COLUMNS = List.of(
            new ColumnDef<>("material", "Material", String.class, Row::name,
                    "A material the final job uses"),
            new ColumnDef<>("quantity", "Quantity", String.class, r -> String.format(Locale.US, "%,d", r.quantity()),
                    "How many the final job uses, after the ME and the structure's bonus"),
            new ColumnDef<>("unitPrice", "Unit Price", String.class, r -> IskFormatter.format(r.unitPrice()),
                    "The price of one unit: the market price when you buy it, what it costs to make when you build it"),
            new ColumnDef<>("total", "Total", String.class, r -> IskFormatter.format(r.total()),
                    "The quantity times the unit price"),
            new ColumnDef<>("source", "Source", String.class, Row::source,
                    "Build or Buy, for the items you could make yourself"),
            new ColumnDef<>("typeId", "Type ID", Integer.class, false, Row::typeId,
                    "EVE's ID of the item type")
    );

    public IndustryMaterialsTableModel() {
        super(COLUMNS);
    }
}
