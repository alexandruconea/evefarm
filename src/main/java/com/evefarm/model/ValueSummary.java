package com.evefarm.model;

public record ValueSummary(
        double total,
        double walletBalance,
        double assetsValue,
        double sellOrdersValue,
        double escrowValue,
        double escrowToCoverValue,
        String bestAssetName,
        double bestAssetValue,
        String bestShipName,
        double bestShipValue,
        String bestModuleName,
        double bestModuleValue
) {
}
