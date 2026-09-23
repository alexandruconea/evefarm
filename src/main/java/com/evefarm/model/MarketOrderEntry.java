package com.evefarm.model;

public record MarketOrderEntry(
        long orderId,
        int typeId,
        boolean isBuyOrder,
        double price,
        long volumeRemain,
        long volumeTotal,
        Double escrow,
        long locationId,
        String issued,
        Integer duration,
        String state,
        String range,
        Long minVolume
) {
}
