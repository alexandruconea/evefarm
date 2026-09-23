package com.evefarm.service;

import com.evefarm.esi.FuzzworkApi;
import com.evefarm.esi.LoyaltyApi;
import com.evefarm.esi.dto.FuzzworkBlueprintDto;
import com.evefarm.esi.dto.LoyaltyOfferDto;
import com.evefarm.model.LpOfferRow;
import com.evefarm.model.LpRequiredItem;
import com.evefarm.model.PriceMode;
import com.evefarm.model.TypeInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

public final class LpOfferPricingService {

    private static final String CATEGORY_BLUEPRINT = "Blueprint";
    private static final int RUNS_PER_COPY = 1;
    private static final int MATERIAL_EFFICIENCY_LEVEL = 5;

    private final LoyaltyApi loyaltyApi;
    private final FuzzworkApi fuzzworkApi;
    private final TypeNameCacheService typeNameCacheService;
    private final PriceService priceService;

    public LpOfferPricingService(LoyaltyApi loyaltyApi, FuzzworkApi fuzzworkApi,
                                  TypeNameCacheService typeNameCacheService, PriceService priceService) {
        this.loyaltyApi = loyaltyApi;
        this.fuzzworkApi = fuzzworkApi;
        this.typeNameCacheService = typeNameCacheService;
        this.priceService = priceService;
    }

    public List<LpOfferRow> listPricedOffers(long corporationId) {
        List<LoyaltyOfferDto> offers = loyaltyApi.listStoreOffers(corporationId);

        Set<Integer> typeIds = new HashSet<>();
        for (LoyaltyOfferDto offer : offers) {
            typeIds.add(offer.typeId());
            for (LoyaltyOfferDto.RequiredItemDto required : offer.requiredItems()) {
                typeIds.add(required.typeId());
            }
        }
        typeNameCacheService.resolveTypes(typeIds);
        priceService.ensureFreshPrices(typeIds);

        Map<Integer, Double> sellVolumes = priceService.getSellVolumes();
        Map<Integer, Double> buyVolumes = priceService.getBuyVolumes();

        List<LpOfferRow> result = new ArrayList<>();
        for (LoyaltyOfferDto offer : offers) {
            result.add(price(offer, sellVolumes, buyVolumes));
        }
        return result;
    }

    private LpOfferRow price(LoyaltyOfferDto offer, Map<Integer, Double> sellVolumes, Map<Integer, Double> buyVolumes) {
        TypeInfo type = typeNameCacheService.resolveType(offer.typeId());

        int outputTypeId = offer.typeId();
        long outputUnits = offer.quantity();
        String buildMaterialsSummary = "";
        List<LpRequiredItem> buildMaterials = List.of();
        Double buildMaterialsCost = 0.0;

        if (CATEGORY_BLUEPRINT.equals(type.categoryName())) {
            long totalRuns = offer.quantity() * (long) RUNS_PER_COPY;
            BlueprintPricing blueprint = priceBlueprintOffer(offer.typeId(), totalRuns);
            if (blueprint != null) {
                outputTypeId = blueprint.productTypeId;
                outputUnits = offer.quantity() * (long) RUNS_PER_COPY * blueprint.productQuantityPerRun;
                buildMaterialsSummary = blueprint.summary;
                buildMaterials = blueprint.materials;
                buildMaterialsCost = blueprint.cost;
            }
        }

        Double sellPrice = toNullable(priceService.getUnitPrice(outputTypeId, PriceMode.SELL_MIN));
        Double buyPrice = toNullable(priceService.getUnitPrice(outputTypeId, PriceMode.BUY_MAX));

        double requiredCost = 0;
        boolean allRequiredPriced = true;
        List<String> summaryParts = new ArrayList<>();
        List<LpRequiredItem> requiredItems = new ArrayList<>();
        for (LoyaltyOfferDto.RequiredItemDto required : offer.requiredItems()) {
            TypeInfo requiredType = typeNameCacheService.resolveType(required.typeId());
            summaryParts.add(required.quantity() + "x " + requiredType.name());
            requiredItems.add(new LpRequiredItem(requiredType.name(), required.quantity()));
            Double requiredPrice = toNullable(priceService.getUnitPrice(required.typeId(), PriceMode.SELL_MIN));
            if (requiredPrice == null) {
                allRequiredPriced = false;
                continue;
            }
            requiredCost += requiredPrice * required.quantity();
        }
        Double requiredItemsCost = offer.requiredItems().isEmpty() ? 0.0 : (allRequiredPriced ? requiredCost : null);

        Double otherCost = combine(requiredItemsCost, buildMaterialsCost);
        Double iskPerLpSell = computeIskPerLp(sellPrice, outputUnits, offer.iskCost(), offer.lpCost(), otherCost);
        Double iskPerLpBuy = computeIskPerLp(buyPrice, outputUnits, offer.iskCost(), offer.lpCost(), otherCost);
        Double profitSell = computeProfit(sellPrice, outputUnits, offer.iskCost(), otherCost);
        Double profitBuy = computeProfit(buyPrice, outputUnits, offer.iskCost(), otherCost);

        Double fivePercentSellVolume = sellVolumes.get(outputTypeId);
        Double fivePercentBuyVolume = buyVolumes.get(outputTypeId);

        return new LpOfferRow(offer.offerId(), offer.typeId(), type.name(), type.categoryName(),
                offer.quantity(), offer.lpCost(), offer.iskCost(), offer.akCost(),
                String.join(", ", summaryParts), requiredItems, requiredItemsCost,
                buildMaterialsSummary, buildMaterials, buildMaterialsCost, sellPrice, buyPrice,
                iskPerLpSell, iskPerLpBuy, profitSell, profitBuy, fivePercentSellVolume,
                fivePercentBuyVolume);
    }

    private record BlueprintPricing(int productTypeId, int productQuantityPerRun, String summary,
                                     List<LpRequiredItem> materials, Double cost) {
    }

    private BlueprintPricing priceBlueprintOffer(int blueprintTypeId, long totalRuns) {
        Optional<FuzzworkBlueprintDto> response = fuzzworkApi.fetchBlueprintMaterials(blueprintTypeId);
        if (response.isEmpty() || response.get().blueprintDetails() == null
                || response.get().blueprintDetails().productTypeId() == null) {
            return null;
        }
        FuzzworkBlueprintDto blueprint = response.get();
        List<FuzzworkBlueprintDto.MaterialEntry> materials = blueprint.activityMaterials() == null ? null
                : blueprint.activityMaterials().get(FuzzworkBlueprintDto.ACTIVITY_MANUFACTURING);
        if (materials == null || materials.isEmpty()) {
            return null;
        }

        double cost = 0;
        boolean allPriced = true;
        List<String> summaryParts = new ArrayList<>();
        List<LpRequiredItem> materialItems = new ArrayList<>();
        for (FuzzworkBlueprintDto.MaterialEntry material : materials) {
            long quantityAtMe = applyMaterialEfficiency(material.quantity(), totalRuns, MATERIAL_EFFICIENCY_LEVEL);
            summaryParts.add(quantityAtMe + "x " + material.name());
            materialItems.add(new LpRequiredItem(material.name(), quantityAtMe));
            Double price = toNullable(priceService.getUnitPrice(material.typeId(), PriceMode.SELL_MIN));
            if (price == null) {
                allPriced = false;
                continue;
            }
            cost += price * quantityAtMe;
        }

        int productQuantity = blueprint.blueprintDetails().productQuantity() == null
                ? 1 : blueprint.blueprintDetails().productQuantity();
        return new BlueprintPricing(blueprint.blueprintDetails().productTypeId(), productQuantity,
                String.join(", ", summaryParts), materialItems, allPriced ? cost : null);
    }

    long applyMaterialEfficiency(long baseQuantity, long runs, int meLevel) {
        double materialModifier = 1.0 - (meLevel * 0.01);
        double roundedTo2 = Math.round(runs * baseQuantity * materialModifier * 100.0) / 100.0;
        return Math.max(runs, (long) Math.ceil(roundedTo2));
    }

    private Double combine(Double a, Double b) {
        if (a == null || b == null) {
            return null;
        }
        return a + b;
    }

    Double computeProfit(Double outputPrice, long outputUnits, double iskCost, Double otherCost) {
        if (outputPrice == null || otherCost == null) {
            return null;
        }
        return outputPrice * outputUnits - iskCost - otherCost;
    }

    Double computeIskPerLp(Double outputPrice, long outputUnits, double iskCost, long lpCost, Double otherCost) {
        if (lpCost <= 0) {
            return null;
        }
        Double profit = computeProfit(outputPrice, outputUnits, iskCost, otherCost);
        return profit == null ? null : profit / lpCost;
    }

    private Double toNullable(OptionalDouble value) {
        return value.isPresent() ? value.getAsDouble() : null;
    }
}
