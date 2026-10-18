package com.evefarm.ui;

import com.evefarm.model.LpOfferRow;
import com.evefarm.service.ItemIconService;
import com.evefarm.ui.column.ColumnDef;
import com.evefarm.ui.column.ColumnTableModel;
import com.evefarm.util.IskFormatter;

import javax.swing.ImageIcon;
import java.util.List;
import java.util.Locale;

public final class LpStoreTableModel extends ColumnTableModel<LpOfferRow> {

    private static List<ColumnDef<LpOfferRow>> columns(ItemIconService iconService) {
        return List.of(
            new ColumnDef<>("icon", "", ImageIcon.class, r -> iconService.iconOrPlaceholder(r.typeId()),
                    "The item's icon"),
            new ColumnDef<>("item", "Item", String.class, LpOfferRow::itemName,
                    "What the offer gives"),
            new ColumnDef<>("category", "Category", String.class, r -> nullToEmpty(r.categoryName()),
                    "The item's category, such as Ship, Module or Blueprint"),
            new ColumnDef<>("quantity", "Quantity", Long.class, LpOfferRow::quantity,
                    "How many units the offer gives"),
            new ColumnDef<>("lpCost", "LP Cost", Long.class, LpOfferRow::lpCost,
                    "The loyalty points the offer costs"),
            new ColumnDef<>("iskCost", "ISK Cost", String.class, r -> IskFormatter.format(r.iskCost()),
                    "The ISK the offer costs on top of the LP"),
            new ColumnDef<>("otherRequirements", "Other Requirements", String.class, r -> nullToEmpty(r.requiredItemsSummary()),
                    "Other items the offer asks for. Right-click a cell and choose Copy to paste them into Multibuy"),
            new ColumnDef<>("otherCost", "Other Cost", String.class, r -> formatIsk(r.requiredItemsCost()),
                    "What the other items cost at the lowest sell price"),
            new ColumnDef<>("buildMaterials", "Build Materials", String.class, r -> nullToEmpty(r.buildMaterialsSummary()),
                    "For a blueprint: the materials to build what it makes. Right-click a cell and choose Copy to "
                            + "paste them into Multibuy"),
            new ColumnDef<>("buildCost", "Build Cost", String.class, r -> formatIsk(r.buildMaterialsCost()),
                    "What the build materials cost"),
            new ColumnDef<>("sellPrice", "Sell Price", String.class, r -> formatIsk(r.sellPricePerUnit()),
                    "The lowest sell price of the item, or of what a blueprint builds"),
            new ColumnDef<>("buyPrice", "Buy Price", String.class, r -> formatIsk(r.buyPricePerUnit()),
                    "The highest buy order price of the item, or of what a blueprint builds"),
            new ColumnDef<>("dailyVolume", "Sold per Day", String.class, r -> formatPerDay(r.dailyVolume()),
                    "How many units people buy from sell orders per day in The Forge, the region of Jita, estimated "
                            + "from the last 30 days of EVE's market history. Sales into buy orders are left out. For "
                            + "a blueprint, of what it builds"),
            new ColumnDef<>("daysToSell", "Days to Sell", String.class, r -> formatDays(r.daysToSell()),
                    "How many days of sales in The Forge the whole offer equals: its units divided by Sold per "
                            + "Day. The higher it is, the longer you may wait to sell it all"),
            new ColumnDef<>("forSale", "For Sale", String.class, false, r -> formatVolume(r.unitsForSale()),
                    "How many units are for sale in Jita 4-4 right now. Only with Fuzzwork prices"),
            new ColumnDef<>("iskPerLpSell", "ISK/LP (Sell)", String.class, r -> formatIskPerLp(r.iskPerLpSell()),
                    "The ISK you make per LP selling at the lowest sell price, after the ISK cost and the other costs"),
            new ColumnDef<>("iskPerLpBuy", "ISK/LP (Buy)", String.class, r -> formatIskPerLp(r.iskPerLpBuy()),
                    "The ISK you make per LP selling to the highest buy order, after the ISK cost and the other costs"),
            new ColumnDef<>("netIskPerLpSell", "Net ISK/LP (Sell)", String.class,
                    r -> formatIskPerLp(r.netIskPerLpSell()),
                    "ISK/LP (Sell) after the sales tax and the broker fee your main character pays in Jita 4-4"),
            new ColumnDef<>("netIskPerLpBuy", "Net ISK/LP (Buy)", String.class,
                    r -> formatIskPerLp(r.netIskPerLpBuy()),
                    "ISK/LP (Buy) after the sales tax your main character pays. "
                            + "Selling to buy orders has no broker fee"),
            new ColumnDef<>("profitSell", "Profit (Sell)", String.class, r -> formatIsk(r.profitSell()),
                    "The ISK you make on the whole offer selling at the lowest sell price"),
            new ColumnDef<>("profitBuy", "Profit (Buy)", String.class, r -> formatIsk(r.profitBuy()),
                    "The ISK you make on the whole offer selling to the highest buy order"),
            new ColumnDef<>("netProfitSell", "Net Profit (Sell)", String.class, r -> formatIsk(r.netProfitSell()),
                    "Profit (Sell) after the sales tax and the broker fee your main character pays in Jita 4-4"),
            new ColumnDef<>("netProfitBuy", "Net Profit (Buy)", String.class, r -> formatIsk(r.netProfitBuy()),
                    "Profit (Buy) after the sales tax your main character pays. "
                            + "Selling to buy orders has no broker fee"),
            new ColumnDef<>("akCost", "AK Cost", String.class, false, r -> r.akCost() > 0 ? IskFormatter.format(r.akCost()) : "",
                    "The Analysis Kredits the offer costs, in the stores that ask for them"),
            new ColumnDef<>("typeId", "Type ID", Integer.class, false, LpOfferRow::typeId,
                    "EVE's ID of the item type"),
            new ColumnDef<>("offerId", "Offer ID", Long.class, false, LpOfferRow::offerId,
                    "EVE's ID of the offer")
        );
    }

    public LpStoreTableModel(ItemIconService iconService) {
        super(columns(iconService));
    }

    private static String formatIskPerLp(Double value) {
        return value == null ? "" : String.format(Locale.US, "%,.2f", value);
    }

    static String formatPerDay(Double value) {
        if (value == null) {
            return "";
        }
        return String.format(Locale.US, value < 10 ? "%,.1f" : "%,.0f", value);
    }

    static String formatDays(Double days) {
        if (days == null) {
            return "";
        }
        if (days.isInfinite()) {
            return "No sales";
        }
        return String.format(Locale.US, days < 10 ? "%,.1f" : "%,.0f", days);
    }

    private static String formatVolume(Double value) {
        return value == null ? "" : String.format(Locale.US, "%,.0f", value);
    }
}
