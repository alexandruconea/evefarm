package com.evefarm.ui;

import com.evefarm.ui.column.ColumnDef;
import com.evefarm.ui.column.ColumnTableModel;
import com.evefarm.util.IskFormatter;

import java.util.List;
import java.util.Locale;

public final class ShoppingListTableModel extends ColumnTableModel<ShoppingListTableModel.Row> {

    public record Row(int typeId, String name, long needed, long inStock, double unitPrice) {

        public long toBuy() {
            return needed - inStock;
        }

        public double cost() {
            return toBuy() * unitPrice;
        }
    }

    private static final List<ColumnDef<Row>> COLUMNS = List.of(
            new ColumnDef<>("item", "Item", String.class, Row::name,
                    "What to buy"),
            new ColumnDef<>("needed", "Needed", String.class, r -> count(r.needed()),
                    "How many the build needs"),
            new ColumnDef<>("inStock", "In Stock", String.class, r -> r.inStock() > 0 ? count(r.inStock()) : "",
                    "How many you already have (see the Stock setting)"),
            new ColumnDef<>("toBuy", "To Buy", String.class, r -> count(r.toBuy()),
                    "What is left to buy: needed less in stock"),
            new ColumnDef<>("unitPrice", "Unit Price", String.class, r -> IskFormatter.format(r.unitPrice()),
                    "The price of one unit on the market"),
            new ColumnDef<>("cost", "Cost", String.class, r -> IskFormatter.format(r.cost()),
                    "To buy times the unit price"),
            new ColumnDef<>("typeId", "Type ID", Integer.class, false, Row::typeId,
                    "EVE's ID of the item type")
    );

    public ShoppingListTableModel() {
        super(COLUMNS);
    }

    private static String count(long value) {
        return String.format(Locale.US, "%,d", value);
    }
}
