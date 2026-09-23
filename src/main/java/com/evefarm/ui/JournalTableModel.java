package com.evefarm.ui;

import com.evefarm.model.JournalRow;
import com.evefarm.ui.column.ColumnDef;
import com.evefarm.ui.column.ColumnTableModel;
import com.evefarm.util.DateUtil;
import com.evefarm.util.IskFormatter;

import java.util.List;
import java.util.Locale;

public final class JournalTableModel extends ColumnTableModel<JournalRow> {

    private static final List<ColumnDef<JournalRow>> COLUMNS = List.of(
            new ColumnDef<>("character", "Character", String.class, JournalRow::characterName),
            new ColumnDef<>("date", "Date", String.class, r -> DateUtil.formatIsoInstant(r.date())),
            new ColumnDef<>("type", "Type", String.class, r -> formatRefType(r.refType())),
            new ColumnDef<>("amount", "Amount", String.class, r -> IskFormatter.format(r.amount())),
            new ColumnDef<>("balance", "Balance", String.class, r -> IskFormatter.format(r.balance())),
            new ColumnDef<>("description", "Description", String.class, r -> nullToEmpty(r.description())),
            new ColumnDef<>("firstParty", "First Party", String.class, r -> nullToEmpty(r.firstPartyName())),
            new ColumnDef<>("secondParty", "Second Party", String.class, r -> nullToEmpty(r.secondPartyName())),
            new ColumnDef<>("characterId", "Character ID", Long.class, false, JournalRow::characterId)
    );

    public JournalTableModel() {
        super(COLUMNS);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String formatRefType(String refType) {
        if (refType == null || refType.isBlank()) {
            return "";
        }
        String[] words = refType.split("_");
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
}
