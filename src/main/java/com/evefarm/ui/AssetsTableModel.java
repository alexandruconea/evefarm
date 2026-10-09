package com.evefarm.ui;

import com.evefarm.model.AssetRow;
import com.evefarm.ui.column.ColumnDef;
import com.evefarm.ui.column.ColumnTableModel;
import com.evefarm.util.IskFormatter;
import com.evefarm.util.Text;

import java.util.List;
import java.util.Locale;

public final class AssetsTableModel extends ColumnTableModel<AssetRow> {

    private static final List<ColumnDef<AssetRow>> COLUMNS = List.of(
            new ColumnDef<>("character", "Character", String.class, AssetRow::characterName,
                    "The character who owns the item"),
            new ColumnDef<>("item", "Item", String.class, AssetRow::typeName,
                    "The item's name"),
            new ColumnDef<>("group", "Group", String.class, r -> nullToEmpty(r.groupName()),
                    "The item's group, such as Frigate or Mineral"),
            new ColumnDef<>("category", "Category", String.class, r -> nullToEmpty(r.categoryName()),
                    "The item's category, such as Ship or Module"),
            new ColumnDef<>("flag", "Flag", String.class, r -> Text.titleCase(r.locationFlag()),
                    "Where the item sits: a hangar, a cargo hold, a fitting slot and so on"),
            new ColumnDef<>("qty", "Qty", Long.class, AssetRow::quantity,
                    "How many units there are"),
            new ColumnDef<>("location", "Location", String.class, AssetRow::locationName,
                    "The station, structure or solar system the item is in"),
            new ColumnDef<>("fittedTo", "Fitted To", String.class, r -> nullToEmpty(r.containerName()),
                    "The ship the item is fitted to, or the container it is in"),
            new ColumnDef<>("volume", "Volume", String.class, r -> formatVolume(r.volume()),
                    "The volume of one unit, in m3"),
            new ColumnDef<>("totalVolume", "Total Volume", String.class, r -> formatVolume(r.volume() * r.quantity()),
                    "The volume of all the units, in m3"),
            new ColumnDef<>("unitPrice", "Unit Price", String.class, r -> IskFormatter.format(r.unitPrice()),
                    "The price of one unit, from your price provider"),
            new ColumnDef<>("totalValue", "Total Value", String.class, r -> IskFormatter.format(r.totalValue()),
                    "The unit price times the quantity"),
            new ColumnDef<>("valuePerVolume", "ISK/m3", String.class, AssetsTableModel::formatValuePerVolume,
                    "What one m3 of the item is worth: the unit price divided by the volume. Handy to choose what to "
                            + "haul"),
            new ColumnDef<>("singleton", "Singleton", String.class, false, r -> r.singleton() ? "Yes" : "No",
                    "Yes when the item is assembled or unique, such as a blueprint copy, so it doesn't stack"),
            new ColumnDef<>("itemId", "Item ID", Long.class, false, AssetRow::itemId,
                    "EVE's ID of this very item"),
            new ColumnDef<>("typeId", "Type ID", Integer.class, false, AssetRow::typeId,
                    "EVE's ID of the item type"),
            new ColumnDef<>("characterId", "Character ID", Long.class, false, AssetRow::characterId,
                    "EVE's ID of the character")
    );

    public AssetsTableModel() {
        super(COLUMNS);
    }

    public double totalValue() {
        return rows().stream().mapToDouble(AssetRow::totalValue).sum();
    }

    private static String formatVolume(double volume) {
        return String.format(Locale.US, "%,.2f m3", volume);
    }

    private static String formatValuePerVolume(AssetRow row) {
        if (row.volume() <= 0) {
            return "";
        }
        return IskFormatter.format(row.unitPrice() / row.volume());
    }
}
