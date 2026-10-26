package com.evefarm.service;

import com.evefarm.db.dao.PriceCacheDao;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.db.dao.TypeCacheDao;
import com.evefarm.esi.FuzzworkApi;
import com.evefarm.esi.JaniceApi;
import com.evefarm.esi.MarketsApi;
import com.evefarm.util.Platform;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

        verify(prices, never()).replaceAllDetailed(ArgumentMatchers.anyMap());
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
    void aJaniceKeySavedOnTheOtherSystemAsksForTheKeyAgain() {
        SettingsDao settings = mock(SettingsDao.class);
        when(settings.getOrDefault(SettingsDao.PRICE_PROVIDER, PriceService.PROVIDER_CCP))
                .thenReturn(PriceService.PROVIDER_JANICE);
        when(settings.getOrDefault(SettingsDao.JANICE_API_KEY, ""))
                .thenReturn(Platform.isWindows() ? "keyring:AAAA" : "dpapi:AAAA");
        PriceService service = new PriceService(mock(MarketsApi.class), mock(FuzzworkApi.class),
                mock(JaniceApi.class), mock(PriceCacheDao.class), mock(TypeCacheDao.class), settings);

        IllegalStateException error = assertThrows(IllegalStateException.class, service::refreshPrices);

        assertTrue(error.getMessage().contains("enter it again in Options > Settings"), error.getMessage());
    }

    @Test
    void screensKeepWorkingWithSavedPricesWhenTheProviderIsDown() {
        SettingsDao settings = mock(SettingsDao.class);
        FuzzworkApi fuzzwork = mock(FuzzworkApi.class);
        PriceCacheDao prices = mock(PriceCacheDao.class);
        when(settings.getOrDefault(SettingsDao.PRICE_PROVIDER, PriceService.PROVIDER_CCP))
                .thenReturn(PriceService.PROVIDER_FUZZWORK);
        when(prices.findStaleTypeIds(eq(List.of(34, 35)), any())).thenReturn(Set.of(35));
        when(fuzzwork.fetchAggregates(List.of(35))).thenThrow(new IllegalStateException("network down"));
        PriceService service = new PriceService(mock(MarketsApi.class), fuzzwork, mock(JaniceApi.class),
                prices, mock(TypeCacheDao.class), settings);

        assertDoesNotThrow(() -> service.ensureFreshPrices(List.of(34, 35)));

        verify(prices, never()).replaceAllDetailed(ArgumentMatchers.anyMap());
    }

    @Test
    void aProviderThatIsDownIsNotAskedAgainByEveryScreenButAManualUpdateStillTries() {
        SettingsDao settings = mock(SettingsDao.class);
        TypeCacheDao types = mock(TypeCacheDao.class);
        FuzzworkApi fuzzwork = mock(FuzzworkApi.class);
        PriceCacheDao prices = mock(PriceCacheDao.class);
        when(settings.getOrDefault(SettingsDao.PRICE_PROVIDER, PriceService.PROVIDER_CCP))
                .thenReturn(PriceService.PROVIDER_FUZZWORK);
        when(types.listAllTypeIds()).thenReturn(List.of(34, 35));
        when(prices.findStaleTypeIds(any(), any())).thenReturn(Set.of(35));
        when(fuzzwork.fetchAggregates(any())).thenThrow(new IllegalStateException("network down"));
        PriceService service = new PriceService(mock(MarketsApi.class), fuzzwork, mock(JaniceApi.class),
                prices, types, settings);

        service.ensureFreshPrices(List.of(34, 35));
        service.ensureFreshPrices(List.of(34, 35));
        assertThrows(IllegalStateException.class, service::refreshPrices);

        verify(fuzzwork, times(1)).fetchAggregates(List.of(35));
        verify(fuzzwork, times(1)).fetchAggregates(List.of(34, 35));
    }

    @Test
    void anItemTheProviderHasNoPriceForIsNotAskedForAgainWithinAnHour() {
        SettingsDao settings = mock(SettingsDao.class);
        FuzzworkApi fuzzwork = mock(FuzzworkApi.class);
        PriceCacheDao prices = mock(PriceCacheDao.class);
        when(settings.getOrDefault(SettingsDao.PRICE_PROVIDER, PriceService.PROVIDER_CCP))
                .thenReturn(PriceService.PROVIDER_FUZZWORK);
        when(prices.findStaleTypeIds(eq(List.of(34, 99)), any())).thenReturn(Set.of(99));
        when(fuzzwork.fetchAggregates(List.of(99))).thenReturn(Map.of());
        PriceService service = new PriceService(mock(MarketsApi.class), fuzzwork, mock(JaniceApi.class),
                prices, mock(TypeCacheDao.class), settings);

        service.ensureFreshPrices(List.of(34, 99));
        service.ensureFreshPrices(List.of(34, 99));

        verify(fuzzwork, times(1)).fetchAggregates(List.of(99));
    }
}
