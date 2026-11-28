package com.evefarm.model;

public record JournalRow(
        long characterId,
        String characterName,
        String date,
        String refType,
        double amount,
        double balance,
        String description,
        Party firstParty,
        Party secondParty,
        boolean corporation
) {

    public record Party(Long id, String name, String category) {
    }
}
