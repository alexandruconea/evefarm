package com.evefarm.model;

public record MarketOrderRow(
        long orderId,
        long characterId,
        String characterName,
        int typeId,
        String typeName,
        String groupName,
        String categoryName,
        boolean isBuyOrder,
        double price,
        long volumeRemain,
        long volumeTotal,
        Double escrow,
        String locationName,
        String issued,
        Integer duration,
        String range,
        Long minVolume,
        double volume,
        Double marketPrice,
        Double marketSellMin,
        Double marketBuyMax,
        Double marketMarginPercent,
        Double marketProfit,
        Boolean outbid,
        Double brokerFee,
        Double brokerFeePercent
) {
}
