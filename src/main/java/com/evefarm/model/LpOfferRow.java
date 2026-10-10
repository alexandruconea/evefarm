package com.evefarm.model;

import java.util.List;

public record LpOfferRow(
        long offerId,
        int typeId,
        String itemName,
        String categoryName,
        long quantity,
        long lpCost,
        double iskCost,
        double akCost,
        String requiredItemsSummary,
        List<LpRequiredItem> requiredItems,
        Double requiredItemsCost,
        String buildMaterialsSummary,
        List<LpRequiredItem> buildMaterials,
        Double buildMaterialsCost,
        Double sellPricePerUnit,
        Double buyPricePerUnit,
        Double iskPerLpSell,
        Double iskPerLpBuy,
        Double profitSell,
        Double profitBuy,
        int outputTypeId,
        long outputUnits,
        Double unitsForSale,
        Double dailyVolume
) {

    public Double daysToSell() {
        if (dailyVolume == null) {
            return null;
        }
        return dailyVolume <= 0 ? Double.POSITIVE_INFINITY : outputUnits / dailyVolume;
    }

    public LpOfferRow withDailyVolume(Double volume) {
        return new LpOfferRow(offerId, typeId, itemName, categoryName, quantity, lpCost, iskCost, akCost,
                requiredItemsSummary, requiredItems, requiredItemsCost, buildMaterialsSummary, buildMaterials,
                buildMaterialsCost, sellPricePerUnit, buyPricePerUnit, iskPerLpSell, iskPerLpBuy, profitSell,
                profitBuy, outputTypeId, outputUnits, unitsForSale, volume);
    }
}
