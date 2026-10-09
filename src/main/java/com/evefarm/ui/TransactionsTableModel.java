package com.evefarm.ui;

import com.evefarm.model.TransactionRow;
import com.evefarm.ui.column.ColumnDef;
import com.evefarm.ui.column.ColumnTableModel;
import com.evefarm.util.DateUtil;
import com.evefarm.util.IskFormatter;

import java.util.List;

public final class TransactionsTableModel extends ColumnTableModel<TransactionRow> {

    private static final List<ColumnDef<TransactionRow>> COLUMNS = List.of(
            new ColumnDef<>("character", "Character", String.class, TransactionRow::characterName,
                    "The character who bought or sold"),
            new ColumnDef<>("date", "Date", String.class, r -> DateUtil.formatIsoInstant(r.date()),
                    "When it happened, in your local time"),
            new ColumnDef<>("side", "Side", String.class, r -> r.isBuy() ? "Buy" : "Sell",
                    "Buy or Sell"),
            new ColumnDef<>("item", "Item", String.class, TransactionRow::typeName,
                    "The item bought or sold"),
            new ColumnDef<>("group", "Group", String.class, r -> nullToEmpty(r.groupName()),
                    "The item's group, such as Frigate or Mineral"),
            new ColumnDef<>("quantity", "Quantity", Long.class, TransactionRow::quantity,
                    "How many units"),
            new ColumnDef<>("price", "Price", String.class, r -> IskFormatter.format(r.price()),
                    "The price of one unit"),
            new ColumnDef<>("total", "Total", String.class, r -> IskFormatter.format(r.price() * r.quantity()),
                    "The price times the quantity"),
            new ColumnDef<>("client", "Client", String.class, r -> nullToEmpty(r.clientName()),
                    "Who you traded with"),
            new ColumnDef<>("location", "Location", String.class, TransactionRow::locationName,
                    "The station or structure where it happened"),
            new ColumnDef<>("typeId", "Type ID", Integer.class, false, TransactionRow::typeId,
                    "EVE's ID of the item type"),
            new ColumnDef<>("characterId", "Character ID", Long.class, false, TransactionRow::characterId,
                    "EVE's ID of the character")
    );

    public TransactionsTableModel() {
        super(COLUMNS);
    }
}
