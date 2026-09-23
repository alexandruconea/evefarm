package com.evefarm.model;

public record LoyaltyPointRow(
        long characterId,
        long corporationId,
        String corporationName,
        long loyaltyPoints
) {
}
