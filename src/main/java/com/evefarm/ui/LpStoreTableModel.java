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
            new ColumnDef<>("icon", "", ImageIcon.class,
                    r -> orPlaceholder(iconService.getIfLoaded(r.typeId()))),
            new ColumnDef<>("item", "Item", String.class, LpOfferRow::itemName),
            new ColumnDef<>("category", "Category", String.class, r -> nullToEmpty(r.categoryName())),
            new ColumnDef<>("quantity", "Quantity", Long.class, LpOfferRow::quantity),
            new ColumnDef<>("lpCost", "LP Cost", Long.class, LpOfferRow::lpCost),
            new ColumnDef<>("iskCost", "ISK Cost", String.class, r -> IskFormatter.format(r.iskCost())),
            new ColumnDef<>("otherRequirements", "Other Requirements", String.class, r -> nullToEmpty(r.requiredItemsSummary())),
            new ColumnDef<>("otherCost", "Other Cost", String.class, r -> formatIsk(r.requiredItemsCost())),
            new ColumnDef<>("buildMaterials", "Build Materials", String.class, r -> nullToEmpty(r.buildMaterialsSummary())),
            new ColumnDef<>("buildCost", "Build Cost", String.class, r -> formatIsk(r.buildMaterialsCost())),
            new ColumnDef<>("sellPrice", "Sell Price", String.class, r -> formatIsk(r.sellPricePerUnit())),
            new ColumnDef<>("buyPrice", "Buy Price", String.class, r -> formatIsk(r.buyPricePerUnit())),
            new ColumnDef<>("fivePercentVolume", "5% Volume", String.class, r -> formatVolume(r.fivePercentSellVolume())),
            new ColumnDef<>("iskPerLpSell", "ISK/LP (Sell)", String.class, r -> formatIskPerLp(r.iskPerLpSell())),
            new ColumnDef<>("iskPerLpBuy", "ISK/LP (Buy)", String.class, r -> formatIskPerLp(r.iskPerLpBuy())),
            new ColumnDef<>("profitSell", "Profit (Sell)", String.class, r -> formatIsk(r.profitSell())),
            new ColumnDef<>("profitBuy", "Profit (Buy)", String.class, r -> formatIsk(r.profitBuy())),
            new ColumnDef<>("akCost", "AK Cost", String.class, false, r -> r.akCost() > 0 ? IskFormatter.format(r.akCost()) : ""),
            new ColumnDef<>("typeId", "Type ID", Integer.class, false, LpOfferRow::typeId),
            new ColumnDef<>("offerId", "Offer ID", Long.class, false, LpOfferRow::offerId)
        );
    }

    public LpStoreTableModel(ItemIconService iconService) {
        super(columns(iconService));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static ImageIcon orPlaceholder(ImageIcon icon) {
        return icon == null ? ItemIconService.BLANK_PLACEHOLDER : icon;
    }

    private static String formatIsk(Double value) {
        return value == null ? "" : IskFormatter.format(value);
    }

    private static String formatIskPerLp(Double value) {
        return value == null ? "" : String.format(Locale.US, "%,.2f", value);
    }

    private static String formatVolume(Double value) {
        return value == null ? "" : String.format(Locale.US, "%,.0f", value);
    }
}
