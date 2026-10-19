package com.evefarm.esi;

import com.evefarm.esi.dto.ContractDto;
import com.evefarm.esi.dto.ContractItemDto;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.List;
import java.util.Map;

public final class ContractsApi {

    private final EsiHttpClient client;

    public ContractsApi(EsiHttpClient client) {
        this.client = client;
    }

    public List<ContractDto> listContracts(long characterId, String accessToken) {
        List<String> pages = client.getAllPages("/characters/" + characterId + "/contracts/", accessToken, Map.of());
        return client.readPages(pages, new TypeReference<List<ContractDto>>() {
        }, "contracts");
    }

    public List<ContractItemDto> listContractItems(long characterId, long contractId, String accessToken) {
        String body = client.get("/characters/" + characterId + "/contracts/" + contractId + "/items/",
                accessToken, Map.of());
        return client.read(body, new TypeReference<List<ContractItemDto>>() {
        }, "the items of contract " + contractId);
    }
}
