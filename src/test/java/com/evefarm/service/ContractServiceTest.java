package com.evefarm.service;

import com.evefarm.auth.AuthService;
import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.db.dao.CharacterDao;
import com.evefarm.db.dao.ContractDao;
import com.evefarm.esi.ContractsApi;
import com.evefarm.esi.EsiException;
import com.evefarm.esi.dto.ContractDto;
import com.evefarm.esi.dto.ContractItemDto;
import com.evefarm.model.ContractItem;
import com.evefarm.model.ContractRow;
import com.evefarm.model.TypeInfo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContractServiceTest {

    private static final int TRITANIUM = 34;
    private static final int MACHARIEL_BLUEPRINT = 17739;
    private static final int PLEX = 44992;

    @Test
    void contractItemsGetNamesPricesAndTheMostValuableComeFirst() {
        ContractsApi api = mock(ContractsApi.class);
        when(api.listContractItems(7L, 99L, "token")).thenReturn(List.of(
                new ContractItemDto(1, TRITANIUM, 10_000, 10_000, true),
                new ContractItemDto(2, MACHARIEL_BLUEPRINT, 1, -2, true),
                new ContractItemDto(3, PLEX, 2, 2, false)));

        List<ContractItem> items = service(api).listContractItems(7L, 99L);

        assertEquals(List.of("Plex", "Tritanium", "Machariel Blueprint (Blueprint Copy)"),
                items.stream().map(ContractItem::name).toList());
        assertEquals(10_000_000.0, items.get(0).totalValue());
        assertFalse(items.get(0).included(), "PLEX is what the issuer asks for in return");
        assertEquals(40_000.0, items.get(1).totalValue());
        assertNull(items.get(2).unitPrice(), "a copy must not be valued at the original blueprint's market price");
        assertTrue(items.get(2).included());
    }

    @Test
    void esiContractItemsAreReadIncludingBlueprintCopies() throws Exception {
        String json = """
                [{"is_included":true,"is_singleton":true,"quantity":1,"raw_quantity":-2,"record_id":1,"type_id":17739},
                 {"is_included":false,"is_singleton":false,"quantity":250,"record_id":2,"type_id":34}]
                """;

        List<ContractItemDto> items = new ObjectMapper().readValue(json, new TypeReference<List<ContractItemDto>>() {
        });

        assertEquals(1, items.get(0).recordId());
        assertEquals(-2, items.get(0).rawQuantity());
        assertTrue(items.get(0).included());
        assertEquals(2, items.get(1).recordId());
        assertNull(items.get(1).rawQuantity());
        assertEquals(250, items.get(1).quantity());
        assertFalse(items.get(1).included());
    }

    @Test
    void theItemsOfEveryContractAreSavedSoTheyCanBeSeenLaterWithoutEve() {
        Database database = new Database(":memory:");
        MigrationRunner.run(database);
        new CharacterDao(database).upsert(7L, "Trader", null, List.of(), "owner");
        ContractDao contracts = new ContractDao(database);
        ContractsApi api = mock(ContractsApi.class);
        when(api.listContracts(7L, "token")).thenReturn(List.of(contract(99L), contract(100L), contract(101L)));
        when(api.listContractItems(7L, 99L, "token")).thenReturn(List.of(
                new ContractItemDto(1, TRITANIUM, 10_000, 10_000, true)));
        when(api.listContractItems(7L, 100L, "token")).thenThrow(new EsiException(404, "{\"error\":\"not found\"}"));
        when(api.listContractItems(7L, 101L, "token")).thenThrow(new EsiException(502, "{\"error\":\"bad gateway\"}"));
        ContractService service = service(api, contracts);

        service.refreshContractsForCharacter(7L);
        service.refreshContractsForCharacter(7L);

        verify(api, times(1)).listContractItems(7L, 99L, "token");
        verify(api, times(1)).listContractItems(7L, 100L, "token");
        verify(api, times(2)).listContractItems(7L, 101L, "token");
        assertEquals(List.of(101L), contracts.contractsWithoutItems(7L), "a server error is tried again next time");

        when(api.listContracts(7L, "token")).thenReturn(List.of());
        service.refreshContractsForCharacter(7L);
        assertEquals(List.of(101L, 100L, 99L), contracts.listRows(null).stream().map(ContractRow::contractId).toList(),
                "contracts EVE no longer lists are kept");
        assertEquals(List.of("Tritanium"), service.listContractItems(7L, 99L).stream().map(ContractItem::name).toList());
        verify(api, times(1)).listContractItems(7L, 99L, "token");
    }

    private static ContractDto contract(long contractId) {
        return new ContractDto(contractId, "item_exchange", "finished", "", null, 1_000.0, null, 1.0,
                "2026-09-" + (contractId - 80) + "T10:00:00Z", "2026-10-20T10:00:00Z", "2026-09-29T11:00:00Z",
                false, 7, 7, 8, 60003760L, 60003760L);
    }

    private static ContractService service(ContractsApi api) {
        return service(api, mock(ContractDao.class));
    }

    private static ContractService service(ContractsApi api, ContractDao contracts) {
        AuthService auth = mock(AuthService.class);
        when(auth.getValidAccessToken(7L)).thenReturn("token");
        TypeNameCacheService types = mock(TypeNameCacheService.class);
        when(types.resolveType(TRITANIUM)).thenReturn(new TypeInfo(TRITANIUM, "Tritanium", "Mineral", "Material", 0.01));
        when(types.resolveType(MACHARIEL_BLUEPRINT)).thenReturn(
                new TypeInfo(MACHARIEL_BLUEPRINT, "Machariel Blueprint", "Battleship Blueprint", "Blueprint", 0.01));
        when(types.resolveType(PLEX)).thenReturn(new TypeInfo(PLEX, "Plex", "Plex", "Special", 0.01));
        PriceService prices = mock(PriceService.class);
        when(prices.getUnitPrice(anyInt())).thenReturn(OptionalDouble.empty());
        when(prices.getUnitPrice(TRITANIUM)).thenReturn(OptionalDouble.of(4.0));
        when(prices.getUnitPrice(MACHARIEL_BLUEPRINT)).thenReturn(OptionalDouble.of(900_000_000.0));
        when(prices.getUnitPrice(PLEX)).thenReturn(OptionalDouble.of(5_000_000.0));
        return new ContractService(auth, api, mock(EntityNameCacheService.class), mock(LocationNameCacheService.class),
                contracts, types, prices);
    }
}
