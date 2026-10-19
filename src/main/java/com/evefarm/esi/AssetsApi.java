package com.evefarm.esi;

import com.evefarm.esi.dto.AssetDto;
import com.evefarm.esi.dto.AssetNameDto;
import com.fasterxml.jackson.core.type.TypeReference;

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
        return client.readPages(pages, new TypeReference<List<AssetDto>>() {
        }, "ESI assets response");
    }

    public List<AssetNameDto> resolveNames(long characterId, String accessToken, List<Long> itemIds) {
        String body = client.postJson(
                "/characters/" + characterId + "/assets/names/", accessToken, itemIds);
        return client.read(body, new TypeReference<List<AssetNameDto>>() {
        }, "ESI assets response");
    }
}
