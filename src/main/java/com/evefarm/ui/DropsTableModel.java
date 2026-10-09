package com.evefarm.ui;

import com.evefarm.model.OfficerDrop;
import com.evefarm.ui.column.ColumnDef;
import com.evefarm.ui.column.ColumnTableModel;
import com.evefarm.util.IskFormatter;

import java.util.List;

public final class DropsTableModel extends ColumnTableModel<OfficerDrop> {

    private static final List<ColumnDef<OfficerDrop>> COLUMNS = List.of(
            new ColumnDef<>("item", "Item", String.class, OfficerDrop::typeName,
                    "The item the officer dropped"),
            new ColumnDef<>("quantity", "Qty", Integer.class, OfficerDrop::quantity,
                    "How many dropped"),
            new ColumnDef<>("unitPrice", "Unit Price", String.class, r -> IskFormatter.format(r.unitPrice()),
                    "The price of one unit: the market price, or the price you typed"),
            new ColumnDef<>("total", "Value", String.class, r -> IskFormatter.format(r.totalValue()),
                    "The unit price times the quantity")
    );

    public DropsTableModel() {
        super(COLUMNS);
    }
}
