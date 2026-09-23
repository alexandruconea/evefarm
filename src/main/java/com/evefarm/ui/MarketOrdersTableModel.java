package com.evefarm.ui;

import com.evefarm.model.MarketOrderRow;
import com.evefarm.ui.column.ColumnDef;
import com.evefarm.ui.column.ColumnTableModel;
import com.evefarm.util.DateUtil;
import com.evefarm.util.IskFormatter;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

public final class MarketOrdersTableModel extends ColumnTableModel<MarketOrderRow> {

    private static final List<ColumnDef<MarketOrderRow>> COLUMNS = List.of(
            new ColumnDef<>("character", "Character", String.class, MarketOrderRow::characterName),
            new ColumnDef<>("item", "Item", String.class, MarketOrderRow::typeName),
            new ColumnDef<>("group", "Group", String.class, r -> nullToEmpty(r.groupName())),
            new ColumnDef<>("category", "Category", String.class, r -> nullToEmpty(r.categoryName())),
            new ColumnDef<>("side", "Side", String.class, r -> r.isBuyOrder() ? "Buy" : "Sell"),
            new ColumnDef<>("price", "Price", String.class, r -> IskFormatter.format(r.price())),
            new ColumnDef<>("outbid", "Outbid", String.class, MarketOrdersTableModel::formatOutbid),
            new ColumnDef<>("quantity", "Quantity", String.class, r -> r.volumeRemain() + " / " + r.volumeTotal()),
            new ColumnDef<>("escrow", "Escrow", String.class, r -> r.escrow() == null ? "" : IskFormatter.format(r.escrow())),
            new ColumnDef<>("brokerFee", "Broker's Fee", String.class, r -> formatIsk(r.brokerFee())),
            new ColumnDef<>("brokerFeePercent", "Broker's Fee %", String.class, MarketOrdersTableModel::formatBrokerFeePercent),
            new ColumnDef<>("location", "Location", String.class, MarketOrderRow::locationName),
            new ColumnDef<>("range", "Range", String.class, r -> formatRange(r.range())),
            new ColumnDef<>("minQuantity", "Min Quantity", String.class, false, r -> r.minVolume() == null ? "" : String.valueOf(r.minVolume())),
            new ColumnDef<>("volume", "Volume", String.class, false, r -> String.format(Locale.US, "%,.2f m3", r.volume())),
            new ColumnDef<>("issued", "Issued", String.class, r -> DateUtil.formatIsoInstant(r.issued())),
            new ColumnDef<>("expires", "Expires", String.class, MarketOrdersTableModel::formatExpiry),
            new ColumnDef<>("marketPrice", "Market Price", String.class, false, r -> formatIsk(r.marketPrice())),
            new ColumnDef<>("marketSellMin", "Market Sell Min", String.class, false, r -> formatIsk(r.marketSellMin())),
            new ColumnDef<>("marketBuyMax", "Market Buy Max", String.class, false, r -> formatIsk(r.marketBuyMax())),
            new ColumnDef<>("marketMargin", "Market Margin %", String.class, false, MarketOrdersTableModel::formatMargin),
            new ColumnDef<>("marketProfit", "Market Profit +", String.class, false, r -> formatIsk(r.marketProfit())),
            new ColumnDef<>("typeId", "Type ID", Integer.class, false, MarketOrderRow::typeId),
            new ColumnDef<>("characterId", "Character ID", Long.class, false, MarketOrderRow::characterId),
            new ColumnDef<>("orderId", "Order ID", Long.class, false, MarketOrderRow::orderId)
    );

    public MarketOrdersTableModel() {
        super(COLUMNS);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String formatIsk(Double value) {
        return value == null ? "" : IskFormatter.format(value);
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
            Instant expiry = Instant.parse(row.issued()).plus(row.duration(), java.time.temporal.ChronoUnit.DAYS);
            return DateUtil.format(expiry);
        } catch (Exception e) {
            return "";
        }
    }
}
