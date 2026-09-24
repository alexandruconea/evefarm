package com.evefarm.service;

import com.evefarm.auth.AuthService;
import com.evefarm.db.dao.AssetDao;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.db.dao.SkillPointFilterDao;
import com.evefarm.db.dao.SnapshotDao;
import com.evefarm.esi.ClonesApi;
import com.evefarm.esi.ContractsApi;
import com.evefarm.esi.IndustryApi;
import com.evefarm.esi.LoyaltyApi;
import com.evefarm.esi.MarketsApi;
import com.evefarm.esi.SkillsApi;
import com.evefarm.esi.WalletApi;
import com.evefarm.esi.dto.SkillsDto;
import com.evefarm.model.SkillPointFilter;
import com.evefarm.model.TrackerSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrackerSnapshotServiceTest {

    private final TrackerSnapshotService service = new TrackerSnapshotService(
            null, null, null, null, null, null, null, null, null, null, null, null, null, null);

    @Test
    void keepsIskPerLpWhenBookDepthCoversTheOffer() {
        assertEquals(9.3, service.liquidIskPerLp(9.3, 25.0, 10));
    }

    @Test
    void keepsIskPerLpWhenBookDepthExactlyMatchesTheOffer() {
        assertEquals(9.3, service.liquidIskPerLp(9.3, 10.0, 10));
    }

    @Test
    void dropsIskPerLpWhenBookDepthIsThinnerThanTheOffer() {
        assertNull(service.liquidIskPerLp(999999.0, 1.0, 10));
    }

    @Test
    void dropsIskPerLpWhenVolumeIsUnknown() {
        assertNull(service.liquidIskPerLp(9.3, null, 10));
    }

    @Test
    void staysNullWhenIskPerLpIsAlreadyNull() {
        assertNull(service.liquidIskPerLp(null, 10.0, 10));
    }

    @Test
    void aFailedSourcePreventsTheSnapshotFromBeingPersisted() {
        AuthService auth = mock(AuthService.class);
        WalletApi wallet = mock(WalletApi.class);
        SnapshotDao snapshots = mock(SnapshotDao.class);
        when(auth.getValidAccessToken(42L)).thenReturn("token");
        when(wallet.getBalance(42L, "token")).thenThrow(new IllegalStateException("ESI down"));
        TrackerSnapshotService tracker = tracker(auth, wallet, mock(AssetDao.class), snapshots);

        assertThrows(IllegalStateException.class, () -> tracker.captureSnapshot(42L));

        verify(snapshots, never()).insert(any());
    }

    @Test
    void aCompleteSnapshotIsPersistedWithRealValues() {
        AuthService auth = mock(AuthService.class);
        WalletApi wallet = mock(WalletApi.class);
        AssetDao assets = mock(AssetDao.class);
        SnapshotDao snapshots = mock(SnapshotDao.class);
        when(auth.getValidAccessToken(42L)).thenReturn("token");
        when(wallet.getBalance(42L, "token")).thenReturn(100.0);
        when(assets.sumTotalValue(42L)).thenReturn(200.0);
        TrackerSnapshotService tracker = tracker(auth, wallet, assets, snapshots);

        TrackerSnapshot snapshot = tracker.captureSnapshot(42L);

        assertEquals(300.0, snapshot.totalValue());
        verify(snapshots).insert(snapshot);
    }

    private static TrackerSnapshotService tracker(AuthService auth, WalletApi wallet, AssetDao assets,
                                                   SnapshotDao snapshots) {
        ClonesApi clones = mock(ClonesApi.class);
        MarketsApi markets = mock(MarketsApi.class);
        ContractsApi contracts = mock(ContractsApi.class);
        IndustryApi industry = mock(IndustryApi.class);
        SkillsApi skills = mock(SkillsApi.class);
        SkillPointFilterDao filters = mock(SkillPointFilterDao.class);
        SettingsDao settings = mock(SettingsDao.class);
        when(clones.listImplants(42L, "token")).thenReturn(List.of());
        when(markets.listCharacterOrders(42L, "token")).thenReturn(List.of());
        when(contracts.listContracts(42L, "token")).thenReturn(List.of());
        when(industry.listActiveJobs(42L, "token")).thenReturn(List.of());
        when(skills.getSkills(42L, "token")).thenReturn(new SkillsDto(0));
        when(filters.find(42L)).thenReturn(new SkillPointFilter(42L, false, 0));
        when(settings.getOrDefault(SettingsDao.LP_STORE_FAVORITE_CORPORATION_ID, "")).thenReturn("");
        return new TrackerSnapshotService(auth, wallet, clones, markets, contracts, industry, skills,
                mock(LoyaltyApi.class), mock(PriceService.class), mock(LpOfferPricingService.class), assets,
                snapshots, filters, settings);
    }
}
