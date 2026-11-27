package com.evefarm.ui;

import com.evefarm.service.ItemIconService;
import com.evefarm.service.LpPurchaseService.Holding;
import com.evefarm.ui.column.ColumnDef;
import com.evefarm.ui.column.ColumnTableModel;
import com.evefarm.ui.column.Isk;
import com.evefarm.util.Text;

import javax.swing.ImageIcon;
import java.util.List;

public final class LpHoldingsTableModel extends ColumnTableModel<Holding> {

    static final String SELL_NOW = "sellNow";

    private static List<ColumnDef<Holding>> columns(ItemIconService iconService) {
        return List.of(
            new ColumnDef<>("icon", "", ImageIcon.class, r -> iconService.iconOrPlaceholder(r.typeId()),
                    "The item's icon"),
            new ColumnDef<>("item", "Item", String.class, Holding::typeName,
                    "The item you bought from LP stores. For a blueprint offer, the item it builds"),
            new ColumnDef<>("remaining", "Left", String.class, r -> Text.count(r.remaining()),
                    "How many units you still have: what you bought less what you sold and used"),
            new ColumnDef<>("bought", "Bought", String.class, r -> Text.count(r.bought()),
                    "How many units you bought in all"),
            new ColumnDef<>("averageCost", "Avg Cost", Isk.class, r -> Isk.of(r.averageCost()),
                    "What one unit cost you in ISK on average: the ISK paid and the other items, without the LP"),
            new ColumnDef<>("breakEven", "Break-even", Isk.class, r -> Isk.of(r.breakEven()),
                    "The lowest sell price that gets your ISK back after the sales tax and broker fee"),
            new ColumnDef<>("target", "Target Price", Isk.class, r -> Isk.of(r.targetPrice()),
                    "The sell price at which what is left makes the target ISK/LP of its purchases, counted like "
                            + "the LP Store tab"),
            new ColumnDef<>(SELL_NOW, "Sell Now", Isk.class, r -> Isk.of(r.sellNow()),
                    "The lowest sell price right now. Green at or above the Target Price, yellow between the "
                            + "Break-even and the Target Price, red below the Break-even"),
            new ColumnDef<>("buyNow", "Buy Now", Isk.class, r -> Isk.of(r.buyNow()),
                    "The highest buy order right now: what you get if you sell at once"),
            new ColumnDef<>("ifSoldNow", "If Sold Now", Isk.class, r -> Isk.signed(r.profitIfSoldNow()),
                    "What you would make on what is left, selling at Sell Now after fees, against what it cost "
                            + "you in ISK"),
            new ColumnDef<>("realized", "Realized", Isk.class, r -> Isk.signed(r.realizedProfit()),
                    "What you made on what you already sold, after fees, against what it cost you in ISK"),
            new ColumnDef<>("dailyVolume", "Sold per Day", String.class,
                    r -> LpStoreTableModel.formatPerDay(r.dailyVolume()),
                    "How many units people buy from sell orders per day in The Forge, estimated from the last 30 "
                            + "days of EVE's market history"),
            new ColumnDef<>("daysToSell", "Days to Sell", String.class,
                    r -> LpStoreTableModel.formatDays(r.daysToSell()),
                    "How many days of sales in The Forge what is left equals"),
            new ColumnDef<>("typeId", "Type ID", Integer.class, false, Holding::typeId,
                    "EVE's ID of the item type")
        );
    }

    public LpHoldingsTableModel(ItemIconService iconService) {
        super(columns(iconService));
    }

}
