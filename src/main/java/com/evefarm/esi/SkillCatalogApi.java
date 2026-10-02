package com.evefarm.esi;

import com.evefarm.esi.dto.TypeDetailsDto;
import com.evefarm.esi.dto.TypeGroupDto;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SkillCatalogApi {

    private static final int IDS_BATCH_SIZE = 500;
    private static final int NAMES_BATCH_SIZE = 1000;

    private final EsiHttpClient client;

    public SkillCatalogApi(EsiHttpClient client) {
        this.client = client;
    }

    public List<Integer> listGroupIds(int categoryId) {
        JsonNode groups = readTree(client.get("/universe/categories/" + categoryId + "/", null, Map.of()))
                .path("groups");
        List<Integer> result = new ArrayList<>();
        groups.forEach(group -> result.add(group.asInt()));
        return result;
    }

    public TypeGroupDto getGroup(int groupId) {
        return read(client.get("/universe/groups/" + groupId + "/", null, Map.of()), TypeGroupDto.class);
    }

    public TypeDetailsDto getType(int typeId) {
        return read(client.get("/universe/types/" + typeId + "/", null, Map.of()), TypeDetailsDto.class);
    }

    public Map<Integer, String> resolveNames(List<Integer> ids) {
        Map<Integer, String> result = new LinkedHashMap<>();
        for (int start = 0; start < ids.size(); start += NAMES_BATCH_SIZE) {
            List<Integer> batch = ids.subList(start, Math.min(start + NAMES_BATCH_SIZE, ids.size()));
            for (JsonNode entry : readTree(client.postJson("/universe/names/", null, batch))) {
                result.put(entry.path("id").asInt(), entry.path("name").asText());
            }
        }
        return result;
    }

    public Map<String, Integer> resolveTypeIds(List<String> names) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int start = 0; start < names.size(); start += IDS_BATCH_SIZE) {
            List<String> batch = names.subList(start, Math.min(start + IDS_BATCH_SIZE, names.size()));
            JsonNode types = readTree(client.postJson("/universe/ids/", null, batch)).path("inventory_types");
            for (JsonNode type : types) {
                result.put(type.path("name").asText().toLowerCase(Locale.ROOT), type.path("id").asInt());
            }
        }
        return result;
    }

    private JsonNode readTree(String json) {
        try {
            return client.objectMapper().readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse ESI universe response", e);
        }
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return client.objectMapper().readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse ESI universe response", e);
        }
    }
}
