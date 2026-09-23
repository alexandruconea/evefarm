package com.evefarm.ui;

import com.evefarm.model.OfficerDrop;
import com.evefarm.ui.column.ColumnDef;
import com.evefarm.ui.column.ColumnTableModel;
import com.evefarm.util.IskFormatter;

import java.util.List;

public final class DropsTableModel extends ColumnTableModel<OfficerDrop> {

    private static final List<ColumnDef<OfficerDrop>> COLUMNS = List.of(
            new ColumnDef<>("item", "Item", String.class, OfficerDrop::typeName),
            new ColumnDef<>("quantity", "Qty", Integer.class, OfficerDrop::quantity),
            new ColumnDef<>("unitPrice", "Unit Price", String.class, r -> IskFormatter.format(r.unitPrice())),
            new ColumnDef<>("total", "Value", String.class, r -> IskFormatter.format(r.totalValue()))
    );

    public DropsTableModel() {
        super(COLUMNS);
    }
}
