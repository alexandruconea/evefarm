package com.evefarm.model;

import java.time.Instant;

public record LpPurchase(
        long purchaseId,
        long characterId,
        String characterName,
        long corporationId,
        String corporationName,
        long offerId,
        int typeId,
        String typeName,
        int offers,
        long units,
        long lpSpent,
        double iskSpent,
        double otherCost,
        Double targetIskPerLp,
        Instant purchasedAt,
        Long journalEntryId,
        long usedUnits,
        String notes
) {

    public double cashCost() {
        return iskSpent + otherCost;
    }

    public double costPerUnit() {
        return units > 0 ? cashCost() / units : 0;
    }
}
