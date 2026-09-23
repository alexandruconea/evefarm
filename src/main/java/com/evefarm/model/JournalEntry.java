package com.evefarm.model;

public record JournalEntry(
        long entryId,
        String date,
        String refType,
        double amount,
        double balance,
        String description,
        String reason,
        Integer firstPartyId,
        Integer secondPartyId,
        double tax,
        Integer taxReceiverId
) {
}
