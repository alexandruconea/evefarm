package com.evefarm.ui;

import com.evefarm.model.ContractRow;
import com.evefarm.ui.column.ColumnDef;
import com.evefarm.ui.column.ColumnTableModel;
import com.evefarm.util.DateUtil;
import com.evefarm.util.IskFormatter;

import javax.swing.Icon;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

public final class ContractsTableModel extends ColumnTableModel<ContractRow> {

    static final String INFO_COLUMN = "info";

    private static final List<ColumnDef<ContractRow>> COLUMNS = List.of(
            new ColumnDef<>(INFO_COLUMN, "Info", Icon.class, r -> Icons.INFO),
            new ColumnDef<>("character", "Character", String.class, ContractRow::characterName),
            new ColumnDef<>("type", "Type", String.class, r -> formatEnum(r.type())),
            new ColumnDef<>("status", "Status", String.class, r -> statusText(r, Instant.now())),
            new ColumnDef<>("title", "Title", String.class, r -> r.title() == null || r.title().isBlank() ? "" : r.title()),
            new ColumnDef<>("issuer", "Issuer", String.class, r -> nullToEmpty(r.issuerName())),
            new ColumnDef<>("assignee", "Assignee", String.class, r -> nullToEmpty(r.assigneeName())),
            new ColumnDef<>("acceptor", "Acceptor", String.class, r -> nullToEmpty(r.acceptorName())),
            new ColumnDef<>("startLocation", "Start Location", String.class, r -> nullToEmpty(r.startLocationName())),
            new ColumnDef<>("endLocation", "End Location", String.class, r -> nullToEmpty(r.endLocationName())),
            new ColumnDef<>("price", "Price", String.class, r -> formatIsk(r.price())),
            new ColumnDef<>("reward", "Reward", String.class, r -> formatIsk(r.reward())),
            new ColumnDef<>("collateral", "Collateral", String.class, r -> formatIsk(r.collateral())),
            new ColumnDef<>("volume", "Volume", String.class, r -> r.volume() == null ? "" : String.format(Locale.US, "%,.2f m3", r.volume())),
            new ColumnDef<>("issued", "Issued", String.class, r -> DateUtil.formatIsoInstant(r.dateIssued())),
            new ColumnDef<>("expired", "Expired", String.class, r -> DateUtil.formatIsoInstant(r.dateExpired())),
            new ColumnDef<>("completed", "Completed", String.class, r -> DateUtil.formatIsoInstant(r.dateCompleted())),
            new ColumnDef<>("contractId", "Contract ID", Long.class, false, ContractRow::contractId),
            new ColumnDef<>("characterId", "Character ID", Long.class, false, ContractRow::characterId)
    );

    public ContractsTableModel() {
        super(COLUMNS);
    }

    static String statusText(ContractRow row, Instant now) {
        if ("outstanding".equals(row.status()) && isPast(row.dateExpired(), now)) {
            return "Expired";
        }
        return formatEnum(row.status());
    }

    private static boolean isPast(String isoInstant, Instant now) {
        try {
            return isoInstant != null && Instant.parse(isoInstant).isBefore(now);
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String formatIsk(Double value) {
        return value == null ? "" : IskFormatter.format(value);
    }

    static String formatEnum(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String[] words = value.split("_");
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
