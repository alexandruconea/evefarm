package com.evefarm.model;

public record BlueprintRow(
        long ownerId,
        String ownerName,
        long itemId,
        int typeId,
        String typeName,
        String groupName,
        int quantity,
        int materialEfficiency,
        int timeEfficiency,
        int runs,
        String locationName,
        String containerName,
        String locationFlag,
        boolean corporation
) {

    public boolean copy() {
        return quantity == -2;
    }

    public long count() {
        return quantity > 0 ? quantity : 1;
    }
}
