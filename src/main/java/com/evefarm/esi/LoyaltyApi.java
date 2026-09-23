package com.evefarm.esi;

import com.evefarm.esi.dto.LoyaltyOfferDto;
import com.evefarm.esi.dto.LoyaltyPointDto;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.List;
import java.util.Map;

public final class LoyaltyApi {

    private final EsiHttpClient client;

    public LoyaltyApi(EsiHttpClient client) {
        this.client = client;
    }

    public List<LoyaltyPointDto> listLoyaltyPoints(long characterId, String accessToken) {
        String body = client.get("/characters/" + characterId + "/loyalty/points/", accessToken, Map.of());
        try {
            return client.objectMapper().readValue(body, new TypeReference<List<LoyaltyPointDto>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse loyalty points", e);
        }
    }

    public List<LoyaltyOfferDto> listStoreOffers(long corporationId) {
        String body = client.get("/loyalty/stores/" + corporationId + "/offers/", null, Map.of());
        try {
            return client.objectMapper().readValue(body, new TypeReference<List<LoyaltyOfferDto>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse loyalty store offers", e);
        }
    }

    public List<Long> listNpcCorporationIds() {
        String body = client.get("/corporations/npccorps/", null, Map.of());
        try {
            return client.objectMapper().readValue(body, new TypeReference<List<Long>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse NPC corporation list", e);
        }
    }
}
