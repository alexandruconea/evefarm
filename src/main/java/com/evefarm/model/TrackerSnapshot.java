package com.evefarm.model;

import java.time.Instant;

public record TrackerSnapshot(
        long characterId,
        Instant capturedAt,
        double walletBalance,
        double assetsValue,
        double implantsValue,
        double sellOrdersValue,
        double escrowValue,
        double escrowToCoverValue,
        double manufacturingValue,
        double contractCollateralValue,
        double contractsValue,
        long skillPoints,
        double skillPointValue,
        double lpValue,
        double totalValue
) {
    public static TrackerSnapshot of(long characterId, Instant capturedAt,
                                      double walletBalance, double assetsValue, double implantsValue,
                                      double sellOrdersValue, double escrowValue, double escrowToCoverValue,
                                      double manufacturingValue, double contractCollateralValue,
                                      double contractsValue, long skillPoints, double skillPointValue,
                                      double lpValue) {
        double total = walletBalance + assetsValue + implantsValue + sellOrdersValue
                + escrowValue + manufacturingValue + contractCollateralValue + contractsValue
                + skillPointValue + lpValue;
        return new TrackerSnapshot(characterId, capturedAt, walletBalance, assetsValue, implantsValue,
                sellOrdersValue, escrowValue, escrowToCoverValue, manufacturingValue, contractCollateralValue,
                contractsValue, skillPoints, skillPointValue, lpValue, total);
    }
}
