package com.evefarm.esi;

import com.evefarm.esi.dto.ContractDto;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ContractsApi {

    private final EsiHttpClient client;

    public ContractsApi(EsiHttpClient client) {
        this.client = client;
    }

    public List<ContractDto> listContracts(long characterId, String accessToken) {
        List<String> pages = client.getAllPages("/characters/" + characterId + "/contracts/", accessToken, Map.of());
        List<ContractDto> result = new ArrayList<>();
        try {
            for (String page : pages) {
                result.addAll(client.objectMapper().readValue(page, new TypeReference<List<ContractDto>>() {
                }));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse contracts", e);
        }
        return result;
    }
}
