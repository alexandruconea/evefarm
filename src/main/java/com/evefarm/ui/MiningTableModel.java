package com.evefarm.ui;

import com.evefarm.model.MiningRow;
import com.evefarm.service.ItemIconService;
import com.evefarm.ui.column.ColumnDef;
import com.evefarm.ui.column.ColumnTableModel;
import com.evefarm.util.IskFormatter;

import javax.swing.ImageIcon;
import java.util.List;
import java.util.Locale;

public final class MiningTableModel extends ColumnTableModel<MiningRow> {

    private static List<ColumnDef<MiningRow>> columns(ItemIconService iconService) {
        return List.of(
            new ColumnDef<>("icon", "", ImageIcon.class, r -> iconService.iconOrPlaceholder(r.typeId())),
            new ColumnDef<>("date", "Date", String.class, MiningRow::date),
            new ColumnDef<>("character", "Character", String.class, MiningRow::characterName),
            new ColumnDef<>("system", "System", String.class, MiningRow::systemName),
            new ColumnDef<>("ore", "Ore", String.class, MiningRow::oreName),
            new ColumnDef<>("kind", "Kind", String.class, MiningRow::kind),
            new ColumnDef<>("quantity", "Quantity", String.class, r -> String.format(Locale.US, "%,d", r.quantity())),
            new ColumnDef<>("volume", "Volume", String.class, r -> formatVolume(r.volume())),
            new ColumnDef<>("unitValue", "Unit Value", String.class,
                    r -> r.unitValue() == null ? "" : IskFormatter.format(r.unitValue())),
            new ColumnDef<>("value", "Value", String.class,
                    r -> r.value() == null ? "" : IskFormatter.format(r.value())),
            new ColumnDef<>("characterId", "Character ID", Long.class, false, MiningRow::characterId)
        );
    }

    public MiningTableModel(ItemIconService iconService) {
        super(columns(iconService));
    }

    static String formatVolume(double volume) {
        return String.format(Locale.US, "%,.1f m3", volume);
    }
}
