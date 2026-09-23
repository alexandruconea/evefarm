package com.evefarm.ui;

import com.evefarm.model.AssetRow;
import com.evefarm.ui.column.ColumnDef;
import com.evefarm.ui.column.ColumnTableModel;
import com.evefarm.util.IskFormatter;

import java.util.List;
import java.util.Locale;

public final class AssetsTableModel extends ColumnTableModel<AssetRow> {

    private static final List<ColumnDef<AssetRow>> COLUMNS = List.of(
            new ColumnDef<>("character", "Character", String.class, AssetRow::characterName),
            new ColumnDef<>("item", "Item", String.class, AssetRow::typeName),
            new ColumnDef<>("group", "Group", String.class, r -> nullToEmpty(r.groupName())),
            new ColumnDef<>("category", "Category", String.class, r -> nullToEmpty(r.categoryName())),
            new ColumnDef<>("flag", "Flag", String.class, r -> formatFlag(r.locationFlag())),
            new ColumnDef<>("qty", "Qty", Long.class, AssetRow::quantity),
            new ColumnDef<>("location", "Location", String.class, AssetRow::locationName),
            new ColumnDef<>("fittedTo", "Fitted To", String.class, r -> nullToEmpty(r.containerName())),
            new ColumnDef<>("volume", "Volume", String.class, r -> formatVolume(r.volume())),
            new ColumnDef<>("totalVolume", "Total Volume", String.class, r -> formatVolume(r.volume() * r.quantity())),
            new ColumnDef<>("unitPrice", "Unit Price", String.class, r -> IskFormatter.format(r.unitPrice())),
            new ColumnDef<>("totalValue", "Total Value", String.class, r -> IskFormatter.format(r.totalValue())),
            new ColumnDef<>("valuePerVolume", "ISK/m3", String.class, AssetsTableModel::formatValuePerVolume),
            new ColumnDef<>("singleton", "Singleton", String.class, false, r -> r.singleton() ? "Yes" : "No"),
            new ColumnDef<>("itemId", "Item ID", Long.class, false, AssetRow::itemId),
            new ColumnDef<>("typeId", "Type ID", Integer.class, false, AssetRow::typeId),
            new ColumnDef<>("characterId", "Character ID", Long.class, false, AssetRow::characterId)
    );

    public AssetsTableModel() {
        super(COLUMNS);
    }

    public double totalValue() {
        return rows().stream().mapToDouble(AssetRow::totalValue).sum();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String formatFlag(String flag) {
        if (flag == null || flag.isBlank()) {
            return "";
        }
        String[] words = flag.replaceAll("([a-z])([A-Z])", "$1 $2").split("[_ ]");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(word.substring(0, 1).toUpperCase(Locale.US)).append(word.substring(1));
        }
        return result.toString();
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
