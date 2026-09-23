package com.evefarm.esi;

import com.evefarm.esi.dto.AssetDto;
import com.evefarm.esi.dto.AssetNameDto;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class AssetsApi {

    private final EsiHttpClient client;

    public AssetsApi(EsiHttpClient client) {
        this.client = client;
    }

    public List<AssetDto> listAssets(long characterId, String accessToken) {
        List<String> pages = client.getAllPages(
                "/characters/" + characterId + "/assets/", accessToken, Map.of());
        List<AssetDto> result = new ArrayList<>();
        for (String page : pages) {
            result.addAll(readList(page, new TypeReference<List<AssetDto>>() {
            }));
        }
        return result;
    }

    public List<AssetNameDto> resolveNames(long characterId, String accessToken, List<Long> itemIds) {
        String body = client.postJson(
                "/characters/" + characterId + "/assets/names/", accessToken, itemIds);
        return readList(body, new TypeReference<List<AssetNameDto>>() {
        });
    }

    private <T> List<T> readList(String json, TypeReference<List<T>> typeReference) {
        try {
            return client.objectMapper().readValue(json, typeReference);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse ESI assets response", e);
        }
    }
}
