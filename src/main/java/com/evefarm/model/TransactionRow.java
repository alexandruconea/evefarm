package com.evefarm.model;

public record TransactionRow(
        long characterId,
        String characterName,
        String date,
        int typeId,
        String typeName,
        String groupName,
        long quantity,
        double price,
        String clientName,
        String locationName,
        boolean isBuy
) {
}
