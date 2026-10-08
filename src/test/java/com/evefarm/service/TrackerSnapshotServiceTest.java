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
import com.evefarm.esi.dto.ContractDto;
import com.evefarm.esi.dto.IndustryJobDto;
import com.evefarm.esi.dto.SkillsDto;
import com.evefarm.model.IndustryActivity;
import com.evefarm.model.SkillPointFilter;
import com.evefarm.model.TrackerSnapshot;
import com.evefarm.model.TypeQuantity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

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
            null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

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

    @Test
    void runningJobsCountEveryUnitTheirRunsMake() {
        AuthService auth = mock(AuthService.class);
        WalletApi wallet = mock(WalletApi.class);
        IndustryApi industry = mock(IndustryApi.class);
        PriceService prices = mock(PriceService.class);
        IndustryCatalogService catalog = mock(IndustryCatalogService.class);
        when(auth.getValidAccessToken(42L)).thenReturn("token");
        when(industry.listActiveJobs(42L, "token")).thenReturn(List.of(
                job(9, 46166, 16672, 2),
                job(1, 1000, 2000, 3)));
        when(catalog.activity(46166, IndustryActivity.REACTION)).thenReturn(Optional.of(new IndustryActivity(46166,
                IndustryActivity.REACTION, 10800, List.of(), List.of(new TypeQuantity(16672, 200)), Map.of(),
                List.of())));
        when(catalog.activity(1000, IndustryActivity.MANUFACTURING)).thenReturn(Optional.empty());
        when(prices.getUnitPrice(16672)).thenReturn(OptionalDouble.of(10.0));
        when(prices.getUnitPrice(2000)).thenReturn(OptionalDouble.of(5.0));
        TrackerSnapshotService tracker = tracker(auth, wallet, mock(AssetDao.class), mock(SnapshotDao.class),
                industry, prices, catalog);

        TrackerSnapshot snapshot = tracker.captureSnapshot(42L);

        assertEquals(2 * 200 * 10.0 + 3 * 5.0, snapshot.manufacturingValue(), 1e-9);
    }

    @Test
    void onlyTheCharactersOwnOpenContractsCount() {
        List<ContractDto> contracts = List.of(
                contract("item_exchange", "outstanding", 42, null, false, 100, 0, 0),
                contract("item_exchange", "outstanding", 7, null, false, 500, 0, 0),
                contract("courier", "outstanding", 42, null, false, 0, 10, 1000),
                contract("courier", "in_progress", 7, 42, false, 0, 50, 2000),
                contract("item_exchange", "outstanding", 42, null, true, 300, 0, 0),
                contract("item_exchange", "finished", 42, 7, false, 900, 0, 0));

        assertEquals(110, TrackerSnapshotService.contractsValue(contracts, 42), 1e-9);
        assertEquals(3000, TrackerSnapshotService.contractCollateralValue(contracts, 42), 1e-9);
    }

    private static IndustryJobDto job(int activityId, int blueprintTypeId, int productTypeId, int runs) {
        return new IndustryJobDto(activityId * 1000L + blueprintTypeId, activityId, null, "active", blueprintTypeId,
                productTypeId, runs, null, null, null, null);
    }

    private static ContractDto contract(String type, String status, Integer issuerId, Integer acceptorId,
                                        boolean forCorporation, double price, double reward, double collateral) {
        return new ContractDto(1, type, status, "", collateral, price, reward, 0.0, null, null, null,
                forCorporation, issuerId, 42, acceptorId, null, null);
    }

    private static TrackerSnapshotService tracker(AuthService auth, WalletApi wallet, AssetDao assets,
                                                   SnapshotDao snapshots) {
        return tracker(auth, wallet, assets, snapshots, mock(IndustryApi.class), mock(PriceService.class),
                mock(IndustryCatalogService.class));
    }

    private static TrackerSnapshotService tracker(AuthService auth, WalletApi wallet, AssetDao assets,
                                                   SnapshotDao snapshots, IndustryApi industry, PriceService prices,
                                                   IndustryCatalogService catalog) {
        ClonesApi clones = mock(ClonesApi.class);
        MarketsApi markets = mock(MarketsApi.class);
        ContractsApi contracts = mock(ContractsApi.class);
        SkillsApi skills = mock(SkillsApi.class);
        SkillPointFilterDao filters = mock(SkillPointFilterDao.class);
        SettingsDao settings = mock(SettingsDao.class);
        when(clones.listImplants(42L, "token")).thenReturn(List.of());
        when(markets.listCharacterOrders(42L, "token")).thenReturn(List.of());
        when(contracts.listContracts(42L, "token")).thenReturn(List.of());
        when(skills.getSkills(42L, "token")).thenReturn(new SkillsDto(0));
        when(filters.find(42L)).thenReturn(new SkillPointFilter(false, 0));
        when(settings.getOrDefault(SettingsDao.LP_STORE_FAVORITE_CORPORATION_ID, "")).thenReturn("");
        return new TrackerSnapshotService(auth, wallet, clones, markets, contracts, industry, skills,
                mock(LoyaltyApi.class), prices, mock(LpOfferPricingService.class), assets,
                snapshots, filters, settings, catalog);
    }
}
