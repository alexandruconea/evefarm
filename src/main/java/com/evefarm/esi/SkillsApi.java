package com.evefarm.esi;

import com.evefarm.esi.dto.SkillsDto;

import java.util.Map;

public final class SkillsApi {

    private final EsiHttpClient client;

    public SkillsApi(EsiHttpClient client) {
        this.client = client;
    }

    public SkillsDto getSkills(long characterId, String accessToken) {
        String body = client.get("/characters/" + characterId + "/skills/", accessToken, Map.of());
        try {
            return client.objectMapper().readValue(body, SkillsDto.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse skills", e);
        }
    }
}
