package com.evefarm.esi;

import com.fasterxml.jackson.core.type.TypeReference;

import java.util.List;
import java.util.Map;

public final class ClonesApi {

    private final EsiHttpClient client;

    public ClonesApi(EsiHttpClient client) {
        this.client = client;
    }

    public List<Integer> listImplants(long characterId, String accessToken) {
        String body = client.get("/characters/" + characterId + "/implants/", accessToken, Map.of());
        try {
            return client.objectMapper().readValue(body, new TypeReference<List<Integer>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse implants", e);
        }
    }
}
