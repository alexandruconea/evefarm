package com.evefarm.ui;

import com.evefarm.model.MarketOrderRow;
import com.evefarm.ui.column.ColumnDef;
import com.evefarm.ui.column.ColumnTableModel;
import com.evefarm.util.DateUtil;
import com.evefarm.util.IskFormatter;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

public final class MarketOrdersTableModel extends ColumnTableModel<MarketOrderRow> {

    private static final List<ColumnDef<MarketOrderRow>> COLUMNS = List.of(
            new ColumnDef<>("character", "Character", String.class, MarketOrderRow::characterName,
                    "The character who placed the order"),
            new ColumnDef<>("item", "Item", String.class, MarketOrderRow::typeName,
                    "The item bought or sold"),
            new ColumnDef<>("group", "Group", String.class, r -> nullToEmpty(r.groupName()),
                    "The item's group, such as Frigate or Mineral"),
            new ColumnDef<>("category", "Category", String.class, r -> nullToEmpty(r.categoryName()),
                    "The item's category, such as Ship or Module"),
            new ColumnDef<>("side", "Side", String.class, r -> r.isBuyOrder() ? "Buy" : "Sell",
                    "Buy order or sell order"),
            new ColumnDef<>("status", "Status", String.class, MarketOrdersTableModel::statusText,
                    "Active, Fulfilled, Expired, Cancelled or Closed"),
            new ColumnDef<>("price", "Price", String.class, r -> IskFormatter.format(r.price()),
                    "Your price for one unit"),
            new ColumnDef<>("outbid", "Outbid", String.class, MarketOrdersTableModel::formatOutbid,
                    "Yes when someone else has a better price at the same station: a lower sell order or a higher "
                            + "buy order. Checked every 15 minutes while EVE Farm runs; your own orders don't count"),
            new ColumnDef<>("competitorPrice", "Competitor Price", String.class, false,
                    r -> formatIsk(r.competitorPrice()),
                    "The best price someone else offers at the same station: the lowest other sell order or the "
                            + "highest other buy order"),
            new ColumnDef<>("quantity", "Quantity", String.class, r -> r.volumeRemain() + " / " + r.volumeTotal(),
                    "The units left / the units at the start"),
            new ColumnDef<>("escrow", "Escrow", String.class, r -> r.escrow() == null ? "" : IskFormatter.format(r.escrow()),
                    "The ISK set aside to pay for a buy order"),
            new ColumnDef<>("brokerFee", "Broker's Fee", String.class, r -> formatIsk(r.brokerFee()),
                    "What you paid to place the order, matched from your wallet journal"),
            new ColumnDef<>("brokerFeePercent", "Broker's Fee %", String.class, MarketOrdersTableModel::formatBrokerFeePercent,
                    "The broker's fee as a share of the order's value"),
            new ColumnDef<>("location", "Location", String.class, MarketOrderRow::locationName,
                    "The station or structure the order is in"),
            new ColumnDef<>("range", "Range", String.class, r -> formatRange(r.range()),
                    "How far a seller can be from a buy order: the station, the solar system, a number of jumps or "
                            + "the region"),
            new ColumnDef<>("minQuantity", "Min Quantity", String.class, false, r -> r.minVolume() == null ? "" : String.valueOf(r.minVolume()),
                    "The fewest units a seller must sell to the buy order at once"),
            new ColumnDef<>("volume", "Volume", String.class, false, r -> String.format(Locale.US, "%,.2f m3", r.volume()),
                    "The volume of one unit, in m3"),
            new ColumnDef<>("issued", "Issued", String.class, r -> DateUtil.formatIsoInstant(r.issued()),
                    "When the order was placed, in your local time"),
            new ColumnDef<>("expires", "Expires", String.class, MarketOrdersTableModel::formatExpiry,
                    "When the order expires, in your local time"),
            new ColumnDef<>("marketPrice", "Market Price", String.class, false, r -> formatIsk(r.marketPrice()),
                    "The item's price from your price provider"),
            new ColumnDef<>("marketSellMin", "Market Sell Min", String.class, false, r -> formatIsk(r.marketSellMin()),
                    "The lowest sell price on the market"),
            new ColumnDef<>("marketBuyMax", "Market Buy Max", String.class, false, r -> formatIsk(r.marketBuyMax()),
                    "The highest buy price on the market"),
            new ColumnDef<>("marketMargin", "Market Margin %", String.class, false, MarketOrdersTableModel::formatMargin,
                    "How far your price is above (+) or below (-) the market price, in percent"),
            new ColumnDef<>("marketProfit", "Market Profit +", String.class, false, r -> formatIsk(r.marketProfit()),
                    "Your price less the market price, for one unit"),
            new ColumnDef<>("typeId", "Type ID", Integer.class, false, MarketOrderRow::typeId,
                    "EVE's ID of the item type"),
            new ColumnDef<>("characterId", "Character ID", Long.class, false, MarketOrderRow::characterId,
                    "EVE's ID of the character"),
            new ColumnDef<>("orderId", "Order ID", Long.class, false, MarketOrderRow::orderId,
                    "EVE's ID of the order")
    );

    public MarketOrdersTableModel() {
        super(COLUMNS);
    }

    static String statusText(MarketOrderRow row) {
        if (row.state() == null) {
            return "";
        }
        return switch (row.state()) {
            case MarketOrderRow.ACTIVE -> "Active";
            case "expired" -> row.volumeRemain() == 0 ? "Fulfilled" : "Expired";
            case "cancelled" -> "Cancelled";
            case MarketOrderRow.CLOSED -> "Closed";
            default -> row.state();
        };
    }

    private static String formatOutbid(MarketOrderRow row) {
        if (row.outbid() == null) {
            return "";
        }
        return row.outbid() ? "Yes" : "No";
    }

    private static String formatMargin(MarketOrderRow row) {
        return row.marketMarginPercent() == null ? ""
                : String.format(Locale.US, "%,.2f%%", row.marketMarginPercent());
    }

    private static String formatBrokerFeePercent(MarketOrderRow row) {
        return row.brokerFeePercent() == null ? ""
                : String.format(Locale.US, "%,.2f%%", row.brokerFeePercent());
    }

    private static String formatRange(String range) {
        if (range == null || range.isBlank()) {
            return "";
        }
        return switch (range) {
            case "station" -> "Station";
            case "solarsystem" -> "Solar System";
            case "region" -> "Region";
            default -> range + (range.matches("\\d+") ? " jumps" : "");
        };
    }

    private static String formatExpiry(MarketOrderRow row) {
        if (row.issued() == null || row.duration() == null) {
            return "";
        }
        try {
            Instant expiry = Instant.parse(row.issued()).plus(row.duration(), ChronoUnit.DAYS);
            return DateUtil.format(expiry);
        } catch (Exception e) {
            return "";
        }
    }
}
