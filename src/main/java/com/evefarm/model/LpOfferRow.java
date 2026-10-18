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
        Double dailyVolume,
        LpNetValues net
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
                profitBuy, outputTypeId, outputUnits, unitsForSale, volume, net);
    }

    public LpOfferRow withNet(LpNetValues values) {
        return new LpOfferRow(offerId, typeId, itemName, categoryName, quantity, lpCost, iskCost, akCost,
                requiredItemsSummary, requiredItems, requiredItemsCost, buildMaterialsSummary, buildMaterials,
                buildMaterialsCost, sellPricePerUnit, buyPricePerUnit, iskPerLpSell, iskPerLpBuy, profitSell,
                profitBuy, outputTypeId, outputUnits, unitsForSale, dailyVolume, values);
    }

    public Double otherCost() {
        return requiredItemsCost == null || buildMaterialsCost == null ? null : requiredItemsCost + buildMaterialsCost;
    }

    public Double netIskPerLpSell() {
        return net == null ? null : net.iskPerLpSell();
    }

    public Double netIskPerLpBuy() {
        return net == null ? null : net.iskPerLpBuy();
    }

    public Double netProfitSell() {
        return net == null ? null : net.profitSell();
    }

    public Double netProfitBuy() {
        return net == null ? null : net.profitBuy();
    }
}
