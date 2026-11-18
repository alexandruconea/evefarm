package com.evefarm.model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.ToDoubleFunction;

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

    public static final Map<String, ToDoubleFunction<TrackerSnapshot>> VALUE_PARTS = valueParts();

    private static Map<String, ToDoubleFunction<TrackerSnapshot>> valueParts() {
        Map<String, ToDoubleFunction<TrackerSnapshot>> parts = new LinkedHashMap<>();
        parts.put("Wallet Balance", TrackerSnapshot::walletBalance);
        parts.put("Assets", TrackerSnapshot::assetsValue);
        parts.put("Implants", TrackerSnapshot::implantsValue);
        parts.put("Sell Orders", TrackerSnapshot::sellOrdersValue);
        parts.put("Escrows", TrackerSnapshot::escrowValue);
        parts.put("Manufacturing", TrackerSnapshot::manufacturingValue);
        parts.put("Contract Collateral", TrackerSnapshot::contractCollateralValue);
        parts.put("Contracts", TrackerSnapshot::contractsValue);
        parts.put("Skill Points", TrackerSnapshot::skillPointValue);
        parts.put("LP Value", TrackerSnapshot::lpValue);
        return Collections.unmodifiableMap(parts);
    }

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
