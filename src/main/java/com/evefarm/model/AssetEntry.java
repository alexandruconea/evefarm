package com.evefarm.model;

public record AssetEntry(
        long itemId,
        int typeId,
        long quantity,
        Long locationId,
        String locationFlag,
        boolean isSingleton,
        String name,
        String containerName,
        double unitPrice,
        double totalValue
) {
}
