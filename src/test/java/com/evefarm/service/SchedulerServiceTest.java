package com.evefarm.service;

import com.evefarm.auth.AuthService;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.db.dao.UpdateCooldownDao;
import com.evefarm.model.EveCharacter;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SchedulerServiceTest {

    @Test
    void failedPriceRefreshDoesNotAdvanceTheCooldown() {
        PriceService prices = mock(PriceService.class);
        UpdateCooldownDao cooldowns = mock(UpdateCooldownDao.class);
        when(cooldowns.findLastRefreshed(UpdateCategories.MARKET_PRICES)).thenReturn(Optional.empty());
        doThrow(new IllegalStateException("network down")).when(prices).refreshPrices();
        SchedulerService scheduler = scheduler(prices, mock(CharacterService.class), mock(AssetService.class),
                mock(TrackerSnapshotService.class), cooldowns);

        scheduler.refreshPrices();

        verify(cooldowns, never()).markRefreshed(UpdateCategories.MARKET_PRICES);
    }

    @Test
    void successfulPriceRefreshAdvancesTheCooldown() {
        PriceService prices = mock(PriceService.class);
        UpdateCooldownDao cooldowns = mock(UpdateCooldownDao.class);
        when(cooldowns.findLastRefreshed(UpdateCategories.MARKET_PRICES)).thenReturn(Optional.empty());
        SchedulerService scheduler = scheduler(prices, mock(CharacterService.class), mock(AssetService.class),
                mock(TrackerSnapshotService.class), cooldowns);

        scheduler.refreshPrices();

        verify(cooldowns).markRefreshed(UpdateCategories.MARKET_PRICES);
    }

    @Test
    void failedCharacterSnapshotDoesNotAdvanceEitherCooldown() {
        CharacterService characters = mock(CharacterService.class);
        AssetService assets = mock(AssetService.class);
        TrackerSnapshotService snapshots = mock(TrackerSnapshotService.class);
        UpdateCooldownDao cooldowns = mock(UpdateCooldownDao.class);
        when(characters.listCharacters()).thenReturn(List.of(character(42L)));
        doThrow(new IllegalStateException("ESI down")).when(assets).refreshAssetsForCharacter(42L);
        SchedulerService scheduler = scheduler(mock(PriceService.class), characters, assets, snapshots, cooldowns);

        scheduler.captureAllSnapshots();

        verify(cooldowns, never()).markRefreshed(UpdateCategories.ASSETS);
        verify(cooldowns, never()).markRefreshed(UpdateCategories.TRACKER);
        verify(snapshots, never()).captureSnapshot(42L);
    }

    @Test
    void allSuccessfulSnapshotsAdvanceBothCooldowns() {
        CharacterService characters = mock(CharacterService.class);
        UpdateCooldownDao cooldowns = mock(UpdateCooldownDao.class);
        when(characters.listCharacters()).thenReturn(List.of(character(42L)));
        SchedulerService scheduler = scheduler(mock(PriceService.class), characters, mock(AssetService.class),
                mock(TrackerSnapshotService.class), cooldowns);

        scheduler.captureAllSnapshots();

        verify(cooldowns).markRefreshed(UpdateCategories.ASSETS);
        verify(cooldowns).markRefreshed(UpdateCategories.TRACKER);
    }

    @Test
    void gamelogsAreScannedAutomaticallyWhenTheFolderExists() {
        KillService kills = mock(KillService.class);
        when(kills.hasGameLogDirectory()).thenReturn(true);

        scheduler(kills).scanGamelogs();

        verify(kills).refreshKillsFromLogs();
    }

    @Test
    void theAutomaticScanStaysQuietWithoutAGamelogFolder() {
        KillService kills = mock(KillService.class);
        when(kills.hasGameLogDirectory()).thenReturn(false);

        scheduler(kills).scanGamelogs();

        verify(kills, never()).refreshKillsFromLogs();
    }

    private static SchedulerService scheduler(KillService kills) {
        return new SchedulerService(mock(AuthService.class), mock(CharacterService.class), mock(PriceService.class),
                mock(AssetService.class), mock(TrackerSnapshotService.class), mock(SettingsDao.class),
                mock(UpdateCooldownDao.class), mock(BackupRestoreService.class), kills);
    }

    private static SchedulerService scheduler(PriceService prices, CharacterService characters,
                                               AssetService assets, TrackerSnapshotService snapshots,
                                               UpdateCooldownDao cooldowns) {
        return new SchedulerService(mock(AuthService.class), characters, prices, assets, snapshots,
                mock(SettingsDao.class), cooldowns, mock(BackupRestoreService.class), mock(KillService.class));
    }

    private static EveCharacter character(long id) {
        return new EveCharacter(id, "Pilot", null, List.of(), Instant.EPOCH, true);
    }
}
