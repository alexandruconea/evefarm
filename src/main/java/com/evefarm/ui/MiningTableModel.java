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
            new ColumnDef<>("icon", "", ImageIcon.class, r -> iconService.iconOrPlaceholder(r.typeId()),
                    "The ore's icon"),
            new ColumnDef<>("date", "Date", String.class, MiningRow::date,
                    "The day it was mined, in EVE time"),
            new ColumnDef<>("character", "Character", String.class, MiningRow::characterName,
                    "The character who mined it"),
            new ColumnDef<>("system", "System", String.class, MiningRow::systemName,
                    "The solar system it was mined in"),
            new ColumnDef<>("ore", "Ore", String.class, MiningRow::oreName,
                    "The ore, ice or gas mined"),
            new ColumnDef<>("kind", "Kind", String.class, MiningRow::kind,
                    "Ore, Moon ore, Ice or Gas"),
            new ColumnDef<>("quantity", "Quantity", String.class, r -> String.format(Locale.US, "%,d", r.quantity()),
                    "How many units were mined"),
            new ColumnDef<>("volume", "Volume", String.class, r -> formatVolume(r.volume()),
                    "The volume mined, in m3"),
            new ColumnDef<>("unitValue", "Unit Value", String.class,
                    r -> r.unitValue() == null ? "" : IskFormatter.format(r.unitValue()),
                    "What one unit is worth, valued as chosen in Value"),
            new ColumnDef<>("value", "Value", String.class,
                    r -> r.value() == null ? "" : IskFormatter.format(r.value()),
                    "The quantity times the unit value"),
            new ColumnDef<>("characterId", "Character ID", Long.class, false, MiningRow::characterId,
                    "EVE's ID of the character")
        );
    }

    public MiningTableModel(ItemIconService iconService) {
        super(columns(iconService));
    }

    static String formatVolume(double volume) {
        return String.format(Locale.US, "%,.1f m3", volume);
    }
}
