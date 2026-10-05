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
            new ColumnDef<>("material", "Material", String.class, Row::name),
            new ColumnDef<>("quantity", "Quantity", String.class, r -> String.format(Locale.US, "%,d", r.quantity())),
            new ColumnDef<>("unitPrice", "Unit Price", String.class, r -> IskFormatter.format(r.unitPrice())),
            new ColumnDef<>("total", "Total", String.class, r -> IskFormatter.format(r.total())),
            new ColumnDef<>("source", "Source", String.class, Row::source),
            new ColumnDef<>("typeId", "Type ID", Integer.class, false, Row::typeId)
    );

    public IndustryMaterialsTableModel() {
        super(COLUMNS);
    }
}
