package com.evefarm.model;

public record LoyaltyPointHistoryRow(
        long characterId,
        String characterName,
        long corporationId,
        String corporationName,
        long loyaltyPoints,
        Long change,
        String recordedAt
) {
}
