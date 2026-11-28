package com.evefarm.esi;

import com.evefarm.esi.dto.AffiliationDto;
import com.evefarm.esi.dto.AssetDto;
import com.evefarm.esi.dto.AssetNameDto;
import com.evefarm.esi.dto.BlueprintDto;
import com.evefarm.esi.dto.CharacterRolesDto;
import com.evefarm.esi.dto.ContractDto;
import com.evefarm.esi.dto.ContractItemDto;
import com.evefarm.esi.dto.CorporationDivisionsDto;
import com.evefarm.esi.dto.CorporationInfoDto;
import com.evefarm.esi.dto.CorporationWalletDto;
import com.evefarm.esi.dto.IndustryJobDto;
import com.evefarm.esi.dto.MarketOrderDto;
import com.evefarm.esi.dto.WalletJournalEntryDto;
import com.evefarm.esi.dto.WalletTransactionDto;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public final class CorporationApi {

    private final EsiHttpClient client;

    public CorporationApi(EsiHttpClient client) {
        this.client = client;
    }

    public List<AffiliationDto> affiliations(Collection<Long> characterIds) {
        String body = client.postJson("/characters/affiliation/", null, List.copyOf(characterIds));
        return client.read(body, new TypeReference<List<AffiliationDto>>() {
        }, "character affiliations");
    }

    public List<String> roles(long characterId, String accessToken) {
        String body = client.get("/characters/" + characterId + "/roles/", accessToken, Map.of());
        CharacterRolesDto roles = client.read(body, CharacterRolesDto.class, "corporation roles");
        return roles.roles() == null ? List.of() : roles.roles();
    }

    public CorporationInfoDto corporation(long corporationId) {
        String body = client.get("/corporations/" + corporationId + "/", null, Map.of());
        return client.read(body, CorporationInfoDto.class, "corporation");
    }

    public List<CorporationWalletDto> wallets(long corporationId, String accessToken) {
        String body = client.get("/corporations/" + corporationId + "/wallets/", accessToken, Map.of());
        return client.read(body, new TypeReference<List<CorporationWalletDto>>() {
        }, "corporation wallets");
    }

    public List<WalletJournalEntryDto> journal(long corporationId, int division, String accessToken) {
        List<String> pages = client.getAllPages(
                "/corporations/" + corporationId + "/wallets/" + division + "/journal/", accessToken, Map.of());
        return client.readPages(pages, new TypeReference<List<WalletJournalEntryDto>>() {
        }, "corporation wallet journal");
    }

    public List<WalletTransactionDto> transactions(long corporationId, int division, String accessToken) {
        String body = client.get("/corporations/" + corporationId + "/wallets/" + division + "/transactions/",
                accessToken, Map.of());
        return client.read(body, new TypeReference<List<WalletTransactionDto>>() {
        }, "corporation wallet transactions");
    }

    public CorporationDivisionsDto divisions(long corporationId, String accessToken) {
        String body = client.get("/corporations/" + corporationId + "/divisions/", accessToken, Map.of());
        return client.read(body, CorporationDivisionsDto.class, "corporation divisions");
    }

    public List<AssetDto> assets(long corporationId, String accessToken) {
        List<String> pages = client.getAllPages("/corporations/" + corporationId + "/assets/", accessToken,
                Map.of());
        return client.readPages(pages, new TypeReference<List<AssetDto>>() {
        }, "corporation assets");
    }

    public List<AssetNameDto> assetNames(long corporationId, String accessToken, List<Long> itemIds) {
        String body = client.postJson("/corporations/" + corporationId + "/assets/names/", accessToken, itemIds);
        return client.read(body, new TypeReference<List<AssetNameDto>>() {
        }, "corporation asset names");
    }

    public List<MarketOrderDto> orders(long corporationId, String accessToken) {
        List<String> pages = client.getAllPages("/corporations/" + corporationId + "/orders/", accessToken,
                Map.of());
        return client.readPages(pages, new TypeReference<List<MarketOrderDto>>() {
        }, "corporation market orders");
    }

    public List<MarketOrderDto> orderHistory(long corporationId, String accessToken) {
        List<String> pages = client.getAllPages("/corporations/" + corporationId + "/orders/history/", accessToken,
                Map.of());
        return client.readPages(pages, new TypeReference<List<MarketOrderDto>>() {
        }, "corporation market order history");
    }

    public List<ContractDto> contracts(long corporationId, String accessToken) {
        List<String> pages = client.getAllPages("/corporations/" + corporationId + "/contracts/", accessToken,
                Map.of());
        return client.readPages(pages, new TypeReference<List<ContractDto>>() {
        }, "corporation contracts");
    }

    public List<ContractItemDto> contractItems(long corporationId, long contractId, String accessToken) {
        String body = client.get("/corporations/" + corporationId + "/contracts/" + contractId + "/items/",
                accessToken, Map.of());
        return client.read(body, new TypeReference<List<ContractItemDto>>() {
        }, "the items of corporation contract " + contractId);
    }

    public List<IndustryJobDto> industryJobs(long corporationId, String accessToken) {
        List<String> pages = client.getAllPages("/corporations/" + corporationId + "/industry/jobs/", accessToken,
                Map.of("include_completed", "true"));
        return client.readPages(pages, new TypeReference<List<IndustryJobDto>>() {
        }, "corporation industry jobs");
    }

    public List<BlueprintDto> blueprints(long corporationId, String accessToken) {
        List<String> pages = client.getAllPages("/corporations/" + corporationId + "/blueprints/", accessToken,
                Map.of());
        return client.readPages(pages, new TypeReference<List<BlueprintDto>>() {
        }, "corporation blueprints");
    }
}
