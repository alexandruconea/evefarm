package com.evefarm.model;

public record MiningLedgerRow(
        long characterId,
        String characterName,
        String date,
        String systemName,
        int typeId,
        String oreName,
        String groupName,
        long quantity,
        double unitVolume
) {
}
