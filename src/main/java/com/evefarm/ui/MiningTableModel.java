package com.evefarm.ui;

import com.evefarm.model.MiningRow;
import com.evefarm.model.RefinedMaterial;
import com.evefarm.service.ItemIconService;
import com.evefarm.ui.column.ColumnDef;
import com.evefarm.ui.column.ColumnTableModel;
import com.evefarm.util.IskFormatter;
import com.evefarm.util.Text;

import javax.swing.ImageIcon;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class MiningTableModel extends ColumnTableModel<MiningRow> {

    private static List<ColumnDef<MiningRow>> columns(ItemIconService iconService) {
        return List.of(
            new ColumnDef<>("icon", "", ImageIcon.class, r -> iconService.iconOrPlaceholder(r.iconTypeId()),
                    "The ore's icon, or the compressed ore's when valued as compressed"),
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
            new ColumnDef<>("quantity", "Quantity", String.class, r -> Text.count(r.quantity()),
                    "How many units were mined"),
            new ColumnDef<>("volume", "Volume", String.class, r -> formatVolume(r.volume()),
                    "The volume mined, in m3"),
            new ColumnDef<>("unitValue", "Unit Value", String.class,
                    r -> r.unitValue() == null ? "" : IskFormatter.format(r.unitValue()),
                    "What one unit is worth, valued as chosen in Value"),
            new ColumnDef<>("value", "Value", String.class,
                    r -> r.value() == null ? "" : IskFormatter.format(r.value()),
                    "The quantity times the unit value"),
            new ColumnDef<>("refinedInto", "Refined Into", String.class, r -> refinedInto(r.materials()),
                    "What it reprocesses into at your refining yield, when valued as refined minerals"),
            new ColumnDef<>("characterId", "Character ID", Long.class, false, MiningRow::characterId,
                    "EVE's ID of the character")
        );
    }

    public MiningTableModel(ItemIconService iconService) {
        super(columns(iconService));
    }

    static String refinedInto(List<RefinedMaterial> materials) {
        return materials.stream()
                .map(material -> material.name() + " " + Text.count(Math.round(material.quantity())))
                .collect(Collectors.joining(", "));
    }

    static String refinedIntoToolTip(List<RefinedMaterial> materials) {
        if (materials.isEmpty()) {
            return null;
        }
        return materials.stream()
                .map(material -> Text.escapeHtml(material.name()) + ": " + Text.count(Math.round(material.quantity()))
                        + (material.value() == null ? " (no price)" : " = " + IskFormatter.format(material.value())))
                .collect(Collectors.joining("<br>", "<html>", "</html>"));
    }

    static String formatVolume(double volume) {
        return String.format(Locale.US, "%,.1f m3", volume);
    }
}
