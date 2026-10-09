package com.evefarm.service;

import com.evefarm.auth.AuthService;
import com.evefarm.db.dao.AssetDao;
import com.evefarm.esi.AssetsApi;
import com.evefarm.esi.dto.AssetDto;
import com.evefarm.esi.dto.AssetNameDto;
import com.evefarm.model.AssetEntry;
import com.evefarm.model.TypeInfo;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssetServiceTest {

    private static final long PILOT = 42L;
    private static final long JITA = 60003760L;

    @Test
    void everyItemIsFiledUnderItsStationWithTheShipOrContainerItIsIn() {
        AuthService auth = mock(AuthService.class);
        AssetsApi assets = mock(AssetsApi.class);
        TypeNameCacheService types = mock(TypeNameCacheService.class);
        PriceService prices = mock(PriceService.class);
        AssetDao dao = mock(AssetDao.class);
        when(auth.getValidAccessToken(PILOT)).thenReturn("token");
        when(assets.listAssets(PILOT, "token")).thenReturn(List.of(
                new AssetDto(1, 11377, 1, JITA, "station", "Hangar", true),
                new AssetDto(2, 2048, 1, 1, "item", "LoSlot0", false),
                new AssetDto(3, 3467, 1, JITA, "station", "Hangar", true),
                new AssetDto(4, 215, 1_000, 3, "item", "Unlocked", false),
                new AssetDto(5, 34, 50, 999, "item", "Hangar", false)));
        when(assets.resolveNames(eq(PILOT), eq("token"), anyList())).thenReturn(List.of(
                new AssetNameDto(1, "My Jaguar"), new AssetNameDto(3, "")));
        when(types.resolveType(3467)).thenReturn(new TypeInfo(3467, "Small Secure Container", null, null, 100));
        when(prices.getUnitPrices()).thenReturn(Map.of(11377, 10_000_000.0, 2048, 500_000.0, 215, 10.0));
        AssetService service = new AssetService(auth, assets, types, mock(LocationNameCacheService.class), prices,
                dao);

        double total = service.refreshAssetsForCharacter(PILOT);

        assertEquals(10_510_000.0, total, 1e-6);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AssetEntry>> saved = ArgumentCaptor.forClass(List.class);
        verify(dao).replaceForCharacter(eq(PILOT), saved.capture());
        Map<Long, AssetEntry> byItem = saved.getValue().stream()
                .collect(Collectors.toMap(AssetEntry::itemId, Function.identity()));
        assertEquals(JITA, byItem.get(2L).locationId(), "a fitted module is filed under the ship's station");
        assertEquals("My Jaguar", byItem.get(2L).containerName());
        assertEquals(JITA, byItem.get(4L).locationId());
        assertEquals("Small Secure Container", byItem.get(4L).containerName(), "an unnamed container shows its type");
        assertEquals(10_000.0, byItem.get(4L).totalValue(), 1e-9);
        assertEquals(999L, byItem.get(5L).locationId(), "an item whose container isn't listed keeps its location");
        assertNull(byItem.get(1L).containerName());
    }
}
