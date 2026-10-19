package com.evefarm.esi;

import com.evefarm.esi.dto.CharacterAttributesDto;
import com.evefarm.esi.dto.CharacterSkillsDto;
import com.evefarm.esi.dto.SkillQueueDto;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.List;
import java.util.Map;

public final class SkillsApi {

    private final EsiHttpClient client;

    public SkillsApi(EsiHttpClient client) {
        this.client = client;
    }

    public CharacterSkillsDto getSkills(long characterId, String accessToken) {
        String body = client.get("/characters/" + characterId + "/skills/", accessToken, Map.of());
        return client.read(body, CharacterSkillsDto.class, "skills");
    }

    public List<SkillQueueDto> getSkillQueue(long characterId, String accessToken) {
        String body = client.get("/characters/" + characterId + "/skillqueue/", accessToken, Map.of());
        return client.read(body, new TypeReference<List<SkillQueueDto>>() {
        }, "the skill queue");
    }

    public CharacterAttributesDto getAttributes(long characterId, String accessToken) {
        String body = client.get("/characters/" + characterId + "/attributes/", accessToken, Map.of());
        return client.read(body, CharacterAttributesDto.class, "attributes");
    }
}
