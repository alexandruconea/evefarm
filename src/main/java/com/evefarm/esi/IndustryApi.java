package com.evefarm.esi;

import com.evefarm.esi.dto.IndustryJobDto;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.List;
import java.util.Map;

public final class IndustryApi {

    private final EsiHttpClient client;

    public IndustryApi(EsiHttpClient client) {
        this.client = client;
    }

    public List<IndustryJobDto> listActiveJobs(long characterId, String accessToken) {
        String body = client.get("/characters/" + characterId + "/industry/jobs/",
                accessToken, Map.of("include_completed", "false"));
        try {
            return client.objectMapper().readValue(body, new TypeReference<List<IndustryJobDto>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse industry jobs", e);
        }
    }
}
