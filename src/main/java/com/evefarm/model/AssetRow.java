package com.evefarm.model;

public record AssetRow(
        long itemId,
        long characterId,
        String characterName,
        int typeId,
        String typeName,
        String groupName,
        String categoryName,
        long quantity,
        String locationName,
        String containerName,
        String locationFlag,
        boolean singleton,
        double volume,
        double unitPrice,
        double totalValue
) {
}
