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
            new ColumnDef<>("fivePercentVolume", "5% Volume", String.class, r -> formatVolume(r.fivePercentSellVolume()),
                    "5% of the units for sale in Jita, to judge whether you could sell the item without flooding the "
                            + "market"),
            new ColumnDef<>("iskPerLpSell", "ISK/LP (Sell)", String.class, r -> formatIskPerLp(r.iskPerLpSell()),
                    "The ISK you make per LP selling at the lowest sell price, after the ISK cost and the other costs"),
            new ColumnDef<>("iskPerLpBuy", "ISK/LP (Buy)", String.class, r -> formatIskPerLp(r.iskPerLpBuy()),
                    "The ISK you make per LP selling to the highest buy order, after the ISK cost and the other costs"),
            new ColumnDef<>("profitSell", "Profit (Sell)", String.class, r -> formatIsk(r.profitSell()),
                    "The ISK you make on the whole offer selling at the lowest sell price"),
            new ColumnDef<>("profitBuy", "Profit (Buy)", String.class, r -> formatIsk(r.profitBuy()),
                    "The ISK you make on the whole offer selling to the highest buy order"),
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

    private static String formatVolume(Double value) {
        return value == null ? "" : String.format(Locale.US, "%,.0f", value);
    }
}
