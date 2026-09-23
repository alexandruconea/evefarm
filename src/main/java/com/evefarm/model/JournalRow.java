package com.evefarm.model;

public record JournalRow(
        long characterId,
        String characterName,
        String date,
        String refType,
        double amount,
        double balance,
        String description,
        String firstPartyName,
        String secondPartyName
) {
}
