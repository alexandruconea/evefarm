package com.evefarm.esi;

import com.evefarm.esi.dto.TypeDetailsDto;
import com.evefarm.esi.dto.TypeGroupDto;
import com.evefarm.util.Chunks;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SkillCatalogApi {

    private static final String RESPONSE = "ESI universe response";
    private static final int IDS_BATCH_SIZE = 500;
    private static final int NAMES_BATCH_SIZE = 1000;

    private final EsiHttpClient client;

    public SkillCatalogApi(EsiHttpClient client) {
        this.client = client;
    }

    public List<Integer> listGroupIds(int categoryId) {
        String body = client.get("/universe/categories/" + categoryId + "/", null, Map.of());
        JsonNode groups = client.readTree(body, RESPONSE).path("groups");
        List<Integer> result = new ArrayList<>();
        groups.forEach(group -> result.add(group.asInt()));
        return result;
    }

    public TypeGroupDto getGroup(int groupId) {
        String body = client.get("/universe/groups/" + groupId + "/", null, Map.of());
        return client.read(body, TypeGroupDto.class, RESPONSE);
    }

    public TypeDetailsDto getType(int typeId) {
        String body = client.get("/universe/types/" + typeId + "/", null, Map.of());
        return client.read(body, TypeDetailsDto.class, RESPONSE);
    }

    public Map<Integer, String> resolveNames(List<Integer> ids) {
        Map<Integer, String> result = new LinkedHashMap<>();
        for (List<Integer> batch : Chunks.of(ids, NAMES_BATCH_SIZE)) {
            for (JsonNode entry : client.readTree(client.postJson("/universe/names/", null, batch), RESPONSE)) {
                result.put(entry.path("id").asInt(), entry.path("name").asText());
            }
        }
        return result;
    }

    public Map<String, Integer> resolveTypeIds(List<String> names) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (List<String> batch : Chunks.of(names, IDS_BATCH_SIZE)) {
            String body = client.postJson("/universe/ids/", null, batch);
            JsonNode types = client.readTree(body, RESPONSE).path("inventory_types");
            for (JsonNode type : types) {
                result.put(type.path("name").asText().toLowerCase(Locale.ROOT), type.path("id").asInt());
            }
        }
        return result;
    }
}
