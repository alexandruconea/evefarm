package com.evefarm.ui;

import com.evefarm.model.TransactionRow;
import com.evefarm.ui.column.ColumnDef;
import com.evefarm.ui.column.ColumnTableModel;
import com.evefarm.util.DateUtil;
import com.evefarm.util.IskFormatter;

import java.util.List;

public final class TransactionsTableModel extends ColumnTableModel<TransactionRow> {

    private static final List<ColumnDef<TransactionRow>> COLUMNS = List.of(
            new ColumnDef<>("character", "Character", String.class, TransactionRow::characterName),
            new ColumnDef<>("date", "Date", String.class, r -> DateUtil.formatIsoInstant(r.date())),
            new ColumnDef<>("side", "Side", String.class, r -> r.isBuy() ? "Buy" : "Sell"),
            new ColumnDef<>("item", "Item", String.class, TransactionRow::typeName),
            new ColumnDef<>("group", "Group", String.class, r -> nullToEmpty(r.groupName())),
            new ColumnDef<>("quantity", "Quantity", Long.class, TransactionRow::quantity),
            new ColumnDef<>("price", "Price", String.class, r -> IskFormatter.format(r.price())),
            new ColumnDef<>("total", "Total", String.class, r -> IskFormatter.format(r.price() * r.quantity())),
            new ColumnDef<>("client", "Client", String.class, r -> nullToEmpty(r.clientName())),
            new ColumnDef<>("location", "Location", String.class, TransactionRow::locationName),
            new ColumnDef<>("typeId", "Type ID", Integer.class, false, TransactionRow::typeId),
            new ColumnDef<>("characterId", "Character ID", Long.class, false, TransactionRow::characterId)
    );

    public TransactionsTableModel() {
        super(COLUMNS);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

}
