package com.evefarm.esi;

import com.evefarm.esi.dto.CharacterAttributesDto;
import com.evefarm.esi.dto.CharacterSkillsDto;
import com.evefarm.esi.dto.SkillQueueDto;
import com.evefarm.esi.dto.SkillsDto;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.List;
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

    public CharacterSkillsDto getCharacterSkills(long characterId, String accessToken) {
        String body = client.get("/characters/" + characterId + "/skills/", accessToken, Map.of());
        try {
            return client.objectMapper().readValue(body, CharacterSkillsDto.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse skills", e);
        }
    }

    public List<SkillQueueDto> getSkillQueue(long characterId, String accessToken) {
        String body = client.get("/characters/" + characterId + "/skillqueue/", accessToken, Map.of());
        try {
            return client.objectMapper().readValue(body, new TypeReference<List<SkillQueueDto>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse the skill queue", e);
        }
    }

    public CharacterAttributesDto getAttributes(long characterId, String accessToken) {
        String body = client.get("/characters/" + characterId + "/attributes/", accessToken, Map.of());
        try {
            return client.objectMapper().readValue(body, CharacterAttributesDto.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse attributes", e);
        }
    }
}
