package com.evefarm.service;

import com.evefarm.esi.IndustryIndexApi;
import com.evefarm.esi.MarketsApi;
import com.evefarm.esi.dto.MarketPriceDto;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public final class IndustryMarketService {

    private static final Duration MAX_AGE = Duration.ofHours(1);

    private final IndustryIndexApi industryIndexApi;
    private final MarketsApi marketsApi;
    private Map<Long, Map<String, Double>> costIndices = Map.of();
    private Instant costIndicesAt = Instant.EPOCH;
    private Map<Integer, Double> adjustedPrices = Map.of();
    private Instant adjustedPricesAt = Instant.EPOCH;

    public IndustryMarketService(IndustryIndexApi industryIndexApi, MarketsApi marketsApi) {
        this.industryIndexApi = industryIndexApi;
        this.marketsApi = marketsApi;
    }

    public synchronized double costIndex(long systemId, String activity) {
        if (costIndicesAt.plus(MAX_AGE).isBefore(Instant.now())) {
            costIndices = industryIndexApi.listCostIndices();
            costIndicesAt = Instant.now();
        }
        return costIndices.getOrDefault(systemId, Map.of()).getOrDefault(activity, 0.0);
    }

    public synchronized Map<Integer, Double> adjustedPrices() {
        if (adjustedPricesAt.plus(MAX_AGE).isBefore(Instant.now())) {
            Map<Integer, Double> prices = new HashMap<>();
            for (MarketPriceDto price : marketsApi.listPrices()) {
                if (price.adjustedPrice() != null) {
                    prices.put(price.typeId(), price.adjustedPrice());
                }
            }
            adjustedPrices = Map.copyOf(prices);
            adjustedPricesAt = Instant.now();
        }
        return adjustedPrices;
    }
}
