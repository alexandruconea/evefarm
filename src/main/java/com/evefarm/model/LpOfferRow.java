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
        Double fivePercentSellVolume,
        Double fivePercentBuyVolume
) {
}
