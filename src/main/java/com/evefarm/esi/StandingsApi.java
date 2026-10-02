package com.evefarm.esi;

import com.evefarm.esi.dto.StandingDto;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.List;
import java.util.Map;

public final class StandingsApi {

    private final EsiHttpClient client;

    public StandingsApi(EsiHttpClient client) {
        this.client = client;
    }

    public List<StandingDto> listStandings(long characterId, String accessToken) {
        String body = client.get("/characters/" + characterId + "/standings/", accessToken, Map.of());
        try {
            return client.objectMapper().readValue(body, new TypeReference<List<StandingDto>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse standings", e);
        }
    }
}
