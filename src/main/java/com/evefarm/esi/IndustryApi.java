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
        return listJobs(characterId, accessToken, false);
    }

    public List<IndustryJobDto> listJobsWithHistory(long characterId, String accessToken) {
        return listJobs(characterId, accessToken, true);
    }

    private List<IndustryJobDto> listJobs(long characterId, String accessToken, boolean includeCompleted) {
        String body = client.get("/characters/" + characterId + "/industry/jobs/",
                accessToken, Map.of("include_completed", String.valueOf(includeCompleted)));
        try {
            return client.objectMapper().readValue(body, new TypeReference<List<IndustryJobDto>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse industry jobs", e);
        }
    }
}
