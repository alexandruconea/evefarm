package com.evefarm.esi;

import com.evefarm.esi.dto.MarketOrderDto;
import com.evefarm.esi.dto.MarketPriceDto;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.List;
import java.util.Map;

public final class MarketsApi {

    private final EsiHttpClient client;

    public MarketsApi(EsiHttpClient client) {
        this.client = client;
    }

    public List<MarketOrderDto> listCharacterOrders(long characterId, String accessToken) {
        String body = client.get("/characters/" + characterId + "/orders/", accessToken, Map.of());
        try {
            return client.objectMapper().readValue(body, new TypeReference<List<MarketOrderDto>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse market orders", e);
        }
    }

    public List<MarketPriceDto> listPrices() {
        String body = client.get("/markets/prices/", null, Map.of());
        try {
            return client.objectMapper().readValue(body, new TypeReference<List<MarketPriceDto>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse market prices", e);
        }
    }
}
