package com.evefarm.ui;

import com.evefarm.model.ContractRow;
import com.evefarm.ui.column.ColumnDef;
import com.evefarm.ui.column.ColumnTableModel;
import com.evefarm.util.DateUtil;
import com.evefarm.util.Text;

import javax.swing.Icon;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

public final class ContractsTableModel extends ColumnTableModel<ContractRow> {

    static final String INFO_COLUMN = "info";

    private static final List<ColumnDef<ContractRow>> COLUMNS = List.of(
            new ColumnDef<>(INFO_COLUMN, "Info", Icon.class, r -> Icons.INFO,
                    "Click the icon to see the items in the contract and what they are worth"),
            new ColumnDef<>("character", "Character", String.class, ContractRow::characterName,
                    "The character the contract belongs to"),
            new ColumnDef<>("type", "Type", String.class, r -> Text.titleCase(r.type()),
                    "Item Exchange, Auction or Courier"),
            new ColumnDef<>("status", "Status", String.class, r -> statusText(r, Instant.now()),
                    "Where the contract stands, such as Outstanding, In Progress, Finished or Expired"),
            new ColumnDef<>("title", "Title", String.class, r -> r.title() == null || r.title().isBlank() ? "" : r.title(),
                    "The contract's description"),
            new ColumnDef<>("issuer", "Issuer", String.class, r -> nullToEmpty(r.issuerName()),
                    "Who made the contract"),
            new ColumnDef<>("assignee", "Assignee", String.class, r -> nullToEmpty(r.assigneeName()),
                    "Who a private contract is for"),
            new ColumnDef<>("acceptor", "Acceptor", String.class, r -> nullToEmpty(r.acceptorName()),
                    "Who accepted the contract"),
            new ColumnDef<>("startLocation", "Start Location", String.class, r -> nullToEmpty(r.startLocationName()),
                    "Where the items are"),
            new ColumnDef<>("endLocation", "End Location", String.class, r -> nullToEmpty(r.endLocationName()),
                    "Where a courier contract delivers to"),
            new ColumnDef<>("price", "Price", String.class, r -> formatIsk(r.price()),
                    "What the buyer pays in an item exchange, or the starting bid of an auction"),
            new ColumnDef<>("reward", "Reward", String.class, r -> formatIsk(r.reward()),
                    "What a courier contract pays for the delivery"),
            new ColumnDef<>("collateral", "Collateral", String.class, r -> formatIsk(r.collateral()),
                    "What the courier puts up, and loses if the delivery fails"),
            new ColumnDef<>("volume", "Volume", String.class, r -> r.volume() == null ? "" : String.format(Locale.US, "%,.2f m3", r.volume()),
                    "The volume of the items, in m3"),
            new ColumnDef<>("issued", "Issued", String.class, r -> DateUtil.formatIsoInstant(r.dateIssued()),
                    "When the contract was made, in your local time"),
            new ColumnDef<>("expired", "Expired", String.class, r -> DateUtil.formatIsoInstant(r.dateExpired()),
                    "When the contract expires, in your local time"),
            new ColumnDef<>("completed", "Completed", String.class, r -> DateUtil.formatIsoInstant(r.dateCompleted()),
                    "When the contract was completed, in your local time"),
            new ColumnDef<>("contractId", "Contract ID", Long.class, false, ContractRow::contractId,
                    "EVE's ID of the contract"),
            new ColumnDef<>("characterId", "Character ID", Long.class, false, ContractRow::characterId,
                    "EVE's ID of the character")
    );

    public ContractsTableModel() {
        super(COLUMNS);
    }

    static String statusText(ContractRow row, Instant now) {
        if ("outstanding".equals(row.status()) && isPast(row.dateExpired(), now)) {
            return "Expired";
        }
        return Text.titleCase(row.status());
    }

    private static boolean isPast(String isoInstant, Instant now) {
        try {
            return isoInstant != null && Instant.parse(isoInstant).isBefore(now);
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}
