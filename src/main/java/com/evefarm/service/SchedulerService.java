package com.evefarm.service;

import com.evefarm.auth.AuthService;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.db.dao.UpdateCooldownDao;
import com.evefarm.model.EveCharacter;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SchedulerService {

    private static final Logger LOG = Logger.getLogger(SchedulerService.class.getName());
    private static final int DEFAULT_SNAPSHOT_INTERVAL_MINUTES = 60;
    private static final Duration MIN_PRICE_REFRESH_INTERVAL = Duration.ofHours(1);

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "evefarm-scheduler");
        t.setDaemon(true);
        return t;
    });
    private final ScheduledExecutorService backupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "evefarm-backup");
        t.setDaemon(true);
        return t;
    });

    private final AuthService authService;
    private final CharacterService characterService;
    private final PriceService priceService;
    private final AssetService assetService;
    private final TrackerSnapshotService trackerSnapshotService;
    private final SettingsDao settingsDao;
    private final UpdateCooldownDao updateCooldownDao;
    private final BackupRestoreService backupRestoreService;

    public SchedulerService(AuthService authService, CharacterService characterService, PriceService priceService,
                             AssetService assetService, TrackerSnapshotService trackerSnapshotService,
                             SettingsDao settingsDao, UpdateCooldownDao updateCooldownDao,
                             BackupRestoreService backupRestoreService) {
        this.authService = authService;
        this.characterService = characterService;
        this.priceService = priceService;
        this.assetService = assetService;
        this.trackerSnapshotService = trackerSnapshotService;
        this.settingsDao = settingsDao;
        this.updateCooldownDao = updateCooldownDao;
        this.backupRestoreService = backupRestoreService;
    }

    public void start() {
        executor.scheduleWithFixedDelay(this::refreshTokens, 0, 5, TimeUnit.MINUTES);
        executor.scheduleWithFixedDelay(this::refreshPrices, 0, 24, TimeUnit.HOURS);
        backupExecutor.scheduleWithFixedDelay(backupRestoreService::autoBackupIfDue, 10, 6 * 60 * 60, TimeUnit.SECONDS);

        int intervalMinutes = settingsDao.get(SettingsDao.SNAPSHOT_INTERVAL_MINUTES)
                .map(Integer::parseInt)
                .orElse(DEFAULT_SNAPSHOT_INTERVAL_MINUTES);
        executor.scheduleWithFixedDelay(this::captureAllSnapshots, 1, intervalMinutes, TimeUnit.MINUTES);
    }

    public void stop() {
        executor.shutdownNow();
        backupExecutor.shutdownNow();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                LOG.warning("Scheduler did not stop within 2s of shutdown - proceeding anyway");
            }
            if (!backupExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                LOG.warning("Backup did not finish within 2s of shutdown - proceeding anyway");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void triggerSnapshotNow() {
        executor.execute(this::captureAllSnapshots);
    }

    private void refreshTokens() {
        forEachCharacter("refresh token", id -> {
            authService.getValidAccessToken(id);
            return null;
        });
    }

    private void refreshPrices() {
        boolean recent = updateCooldownDao.findLastRefreshed(UpdateCategories.MARKET_PRICES)
                .map(last -> last.plus(MIN_PRICE_REFRESH_INTERVAL).isAfter(Instant.now()))
                .orElse(false);
        if (recent) {
            return;
        }
        try {
            priceService.refreshPrices();
            updateCooldownDao.markRefreshed(UpdateCategories.MARKET_PRICES);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to refresh market prices", e);
        }
    }

    private void captureAllSnapshots() {
        forEachCharacter("capture snapshot", id -> {
            assetService.refreshAssetsForCharacter(id);
            trackerSnapshotService.captureSnapshot(id);
            return null;
        });
        updateCooldownDao.markRefreshed(UpdateCategories.ASSETS);
        updateCooldownDao.markRefreshed(UpdateCategories.TRACKER);
    }

    private void forEachCharacter(String action, java.util.function.Function<Long, Void> work) {
        List<EveCharacter> characters = characterService.listCharacters();
        for (EveCharacter character : characters) {
            try {
                work.apply(character.characterId());
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to " + action + " for character " + character.characterId(), e);
            }
        }
    }
}
