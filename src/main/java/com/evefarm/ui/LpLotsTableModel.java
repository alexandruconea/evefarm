package com.evefarm.ui;

import com.evefarm.service.LpPurchaseService.Lot;
import com.evefarm.ui.column.ColumnDef;
import com.evefarm.ui.column.ColumnTableModel;
import com.evefarm.util.DateUtil;
import com.evefarm.util.IskFormatter;

import java.util.List;
import java.util.Locale;

import static com.evefarm.util.Text.count;

public final class LpLotsTableModel extends ColumnTableModel<Lot> {

    private static final List<ColumnDef<Lot>> COLUMNS = List.of(
            new ColumnDef<>("date", "Bought On", String.class, r -> DateUtil.format(r.purchase().purchasedAt()),
                    "When you bought it, in your local time"),
            new ColumnDef<>("item", "Item", String.class, r -> r.purchase().typeName(),
                    "The item you bought"),
            new ColumnDef<>("character", "Character", String.class, r -> r.purchase().characterName(),
                    "The character who bought it"),
            new ColumnDef<>("corporation", "Corporation", String.class, false, r -> r.purchase().corporationName(),
                    "The corporation whose LP store sold it"),
            new ColumnDef<>("offers", "Offers", Integer.class, false, r -> r.purchase().offers(),
                    "How many times you bought the offer"),
            new ColumnDef<>("units", "Units", String.class, r -> count(r.purchase().units()),
                    "How many units the purchase gave you"),
            new ColumnDef<>("remaining", "Left", String.class, r -> count(r.remaining()),
                    "How many units of this purchase are left: units less sold and used"),
            new ColumnDef<>("sold", "Sold", String.class, r -> count(r.sold()),
                    "How many units were sold, matched oldest purchase first with your market sales"),
            new ColumnDef<>("used", "Used", String.class, false, r -> count(r.purchase().usedUnits()),
                    "How many units you marked as used instead of sold"),
            new ColumnDef<>("lp", "LP Spent", String.class, false, r -> count(r.purchase().lpSpent()),
                    "The loyalty points the purchase cost"),
            new ColumnDef<>("isk", "ISK Spent", String.class, false,
                    r -> IskFormatter.format(r.purchase().iskSpent()),
                    "The ISK paid to the LP store"),
            new ColumnDef<>("other", "Other Cost", String.class, false,
                    r -> IskFormatter.format(r.purchase().otherCost()),
                    "What the other required items and build materials cost when you bought it"),
            new ColumnDef<>("costPerUnit", "Cost per Unit", String.class,
                    r -> IskFormatter.format(r.purchase().costPerUnit()),
                    "What one unit cost you in ISK: the ISK paid and the other items, without the LP"),
            new ColumnDef<>("targetIskPerLp", "Target ISK/LP", String.class,
                    r -> r.purchase().targetIskPerLp() == null ? ""
                            : String.format(Locale.US, "%,.2f", r.purchase().targetIskPerLp()),
                    "The ISK/LP you aim for with this purchase. It starts as the ISK/LP (Sell) the LP Store tab "
                            + "showed when you recorded it"),
            new ColumnDef<>("breakEven", "Break-even", String.class, r -> IskFormatter.format(r.breakEven()),
                    "The lowest sell price that gets your ISK back after the sales tax and broker fee"),
            new ColumnDef<>("target", "Target Price", String.class, r -> formatIsk(r.targetPrice()),
                    "The sell price at which the purchase makes the target ISK/LP, counted like the LP Store tab"),
            new ColumnDef<>("iskPerLpNow", "ISK/LP Now", String.class,
                    r -> r.iskPerLpNow() == null ? "" : String.format(Locale.US, "%,.2f", r.iskPerLpNow()),
                    "The ISK/LP the purchase makes at the lowest sell price right now, counted like the LP Store "
                            + "tab"),
            new ColumnDef<>("ifSoldNow", "If Sold Now", String.class, r -> formatIsk(r.profitIfSoldNow()),
                    "What you would make on what is left of this purchase, selling at the lowest sell price now"),
            new ColumnDef<>("realized", "Realized", String.class, r -> IskFormatter.format(r.realizedProfit()),
                    "What you made on the units already sold, after fees"),
            new ColumnDef<>("notes", "Notes", String.class, r -> nullToEmpty(r.purchase().notes()),
                    "Your notes on the purchase"),
            new ColumnDef<>("purchaseId", "Purchase ID", Long.class, false, r -> r.purchase().purchaseId(),
                    "EVE Farm's number for the purchase")
    );

    public LpLotsTableModel() {
        super(COLUMNS);
    }
}
