package com.evefarm.service;

import com.evefarm.auth.AuthService;
import com.evefarm.db.dao.ContractDao;
import com.evefarm.esi.ContractsApi;
import com.evefarm.esi.dto.ContractItemDto;
import com.evefarm.model.ContractItem;
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
import static org.mockito.Mockito.when;

class ContractServiceTest {

    private static final int TRITANIUM = 34;
    private static final int MACHARIEL_BLUEPRINT = 17739;
    private static final int PLEX = 44992;

    @Test
    void contractItemsGetNamesPricesAndTheMostValuableComeFirst() {
        ContractsApi api = mock(ContractsApi.class);
        when(api.listContractItems(7L, 99L, "token")).thenReturn(List.of(
                new ContractItemDto(TRITANIUM, 10_000, 10_000, true),
                new ContractItemDto(MACHARIEL_BLUEPRINT, 1, -2, true),
                new ContractItemDto(PLEX, 2, 2, false)));

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

        assertTrue(items.get(0).blueprintCopy());
        assertTrue(items.get(0).included());
        assertFalse(items.get(1).blueprintCopy());
        assertEquals(250, items.get(1).quantity());
        assertFalse(items.get(1).included());
    }

    private static ContractService service(ContractsApi api) {
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
                mock(ContractDao.class), types, prices);
    }
}
