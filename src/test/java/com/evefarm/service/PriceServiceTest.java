package com.evefarm.service;

import com.evefarm.db.dao.PriceCacheDao;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.db.dao.TypeCacheDao;
import com.evefarm.esi.FuzzworkApi;
import com.evefarm.esi.JaniceApi;
import com.evefarm.esi.MarketsApi;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PriceServiceTest {

    @Test
    void providerFailureIsPropagatedAndDoesNotReplaceTheCache() {
        SettingsDao settings = mock(SettingsDao.class);
        TypeCacheDao types = mock(TypeCacheDao.class);
        FuzzworkApi fuzzwork = mock(FuzzworkApi.class);
        PriceCacheDao prices = mock(PriceCacheDao.class);
        when(settings.getOrDefault(SettingsDao.PRICE_PROVIDER, PriceService.PROVIDER_CCP))
                .thenReturn(PriceService.PROVIDER_FUZZWORK);
        when(types.listAllTypeIds()).thenReturn(List.of(34));
        when(fuzzwork.fetchAggregates(List.of(34))).thenThrow(new IllegalStateException("network down"));
        PriceService service = new PriceService(mock(MarketsApi.class), fuzzwork, mock(JaniceApi.class),
                prices, types, settings);

        assertThrows(IllegalStateException.class, service::refreshPrices);

        verify(prices, never()).replaceAllDetailed(org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void selectingJaniceWithoutAKeyIsARefreshFailure() {
        SettingsDao settings = mock(SettingsDao.class);
        when(settings.getOrDefault(SettingsDao.PRICE_PROVIDER, PriceService.PROVIDER_CCP))
                .thenReturn(PriceService.PROVIDER_JANICE);
        when(settings.getOrDefault(SettingsDao.JANICE_API_KEY, "")).thenReturn("");
        PriceService service = new PriceService(mock(MarketsApi.class), mock(FuzzworkApi.class),
                mock(JaniceApi.class), mock(PriceCacheDao.class), mock(TypeCacheDao.class), settings);

        assertThrows(IllegalStateException.class, service::refreshPrices);
    }

    @Test
    void screensKeepWorkingWithSavedPricesWhenTheProviderIsDown() {
        SettingsDao settings = mock(SettingsDao.class);
        FuzzworkApi fuzzwork = mock(FuzzworkApi.class);
        PriceCacheDao prices = mock(PriceCacheDao.class);
        when(settings.getOrDefault(SettingsDao.PRICE_PROVIDER, PriceService.PROVIDER_CCP))
                .thenReturn(PriceService.PROVIDER_FUZZWORK);
        when(prices.findMissingTypeIds(List.of(34, 35))).thenReturn(Set.of(35));
        when(fuzzwork.fetchAggregates(List.of(35))).thenThrow(new IllegalStateException("network down"));
        PriceService service = new PriceService(mock(MarketsApi.class), fuzzwork, mock(JaniceApi.class),
                prices, mock(TypeCacheDao.class), settings);

        assertDoesNotThrow(() -> service.ensureFreshPrices(List.of(34, 35)));

        verify(prices, never()).replaceAllDetailed(org.mockito.ArgumentMatchers.anyMap());
    }
}
