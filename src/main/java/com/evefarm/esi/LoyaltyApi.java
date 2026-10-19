package com.evefarm.esi;

import com.evefarm.esi.dto.LoyaltyOfferDto;
import com.evefarm.esi.dto.LoyaltyPointDto;
import com.fasterxml.jackson.core.type.TypeReference;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public final class LoyaltyApi {

    private final EsiHttpClient client;

    public LoyaltyApi(EsiHttpClient client) {
        this.client = client;
    }

    public record LoyaltyPoints(List<LoyaltyPointDto> points, Duration age, Duration freshFor) {
    }

    public LoyaltyPoints readLoyaltyPoints(long characterId, String accessToken) {
        EsiHttpClient.Cached response = client.getCached("/characters/" + characterId + "/loyalty/points/",
                accessToken, Map.of());
        return new LoyaltyPoints(client.read(response.body(), new TypeReference<List<LoyaltyPointDto>>() {
        }, "loyalty points"), response.age(), response.freshFor());
    }

    public List<LoyaltyOfferDto> listStoreOffers(long corporationId) {
        String body = client.get("/loyalty/stores/" + corporationId + "/offers/", null, Map.of());
        return client.read(body, new TypeReference<List<LoyaltyOfferDto>>() {
        }, "loyalty store offers");
    }

    public List<Long> listNpcCorporationIds() {
        String body = client.get("/corporations/npccorps/", null, Map.of());
        return client.read(body, new TypeReference<List<Long>>() {
        }, "NPC corporation list");
    }
}
