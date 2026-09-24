package com.evefarm.service;

import com.evefarm.auth.AuthService;
import com.evefarm.db.dao.UpdateCooldownDao;
import com.evefarm.model.EveCharacter;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SchedulerService {

    private static final Logger LOG = Logger.getLogger(SchedulerService.class.getName());
    private static final Duration MIN_PRICE_REFRESH_INTERVAL = Duration.ofHours(1);
    private static final int GAMELOG_SCAN_SECONDS = 600;

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
    private final ScheduledExecutorService gamelogExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "evefarm-gamelogs");
        t.setDaemon(true);
        return t;
    });

    private final AuthService authService;
    private final CharacterService characterService;
    private final PriceService priceService;
    private final AssetService assetService;
    private final TrackerSnapshotService trackerSnapshotService;
    private final UpdateCooldownDao updateCooldownDao;
    private final BackupRestoreService backupRestoreService;
    private final KillService killService;
    private final List<Runnable> snapshotListeners = new CopyOnWriteArrayList<>();

    public SchedulerService(AuthService authService, CharacterService characterService, PriceService priceService,
                             AssetService assetService, TrackerSnapshotService trackerSnapshotService,
                             UpdateCooldownDao updateCooldownDao, BackupRestoreService backupRestoreService,
                             KillService killService) {
        this.authService = authService;
        this.characterService = characterService;
        this.priceService = priceService;
        this.assetService = assetService;
        this.trackerSnapshotService = trackerSnapshotService;
        this.updateCooldownDao = updateCooldownDao;
        this.backupRestoreService = backupRestoreService;
        this.killService = killService;
    }

    public void start() {
        executor.scheduleWithFixedDelay(this::refreshTokens, 0, 5, TimeUnit.MINUTES);
        executor.scheduleWithFixedDelay(this::refreshPrices, 0, 24, TimeUnit.HOURS);
        backupExecutor.scheduleWithFixedDelay(backupRestoreService::autoBackupIfDue, 10, 6 * 60 * 60, TimeUnit.SECONDS);
        executor.schedule(this::captureAllSnapshots, 0, TimeUnit.SECONDS);
        gamelogExecutor.scheduleWithFixedDelay(this::scanGamelogs, 15, GAMELOG_SCAN_SECONDS, TimeUnit.SECONDS);
    }

    public void addSnapshotListener(Runnable listener) {
        snapshotListeners.add(listener);
    }

    public void stop() {
        executor.shutdownNow();
        backupExecutor.shutdownNow();
        gamelogExecutor.shutdownNow();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                LOG.warning("Scheduler did not stop within 2s of shutdown - proceeding anyway");
            }
            if (!backupExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                LOG.warning("Backup did not finish within 2s of shutdown - proceeding anyway");
            }
            if (!gamelogExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                LOG.warning("Gamelog scan did not finish within 2s of shutdown - proceeding anyway");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    void scanGamelogs() {
        if (!killService.hasGameLogDirectory()) {
            return;
        }
        try {
            killService.refreshKillsFromLogs();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Automatic Gamelog scan failed", e);
        }
    }

    private void refreshTokens() {
        forEachCharacter("refresh token", id -> {
            authService.getValidAccessToken(id);
            return null;
        });
    }

    void refreshPrices() {
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

    void captureAllSnapshots() {
        boolean allSucceeded = forEachCharacter("capture snapshot", id -> {
            assetService.refreshAssetsForCharacter(id);
            trackerSnapshotService.captureSnapshot(id);
            return null;
        });
        if (allSucceeded) {
            updateCooldownDao.markRefreshed(UpdateCategories.ASSETS);
            updateCooldownDao.markRefreshed(UpdateCategories.TRACKER);
        }
        for (Runnable listener : snapshotListeners) {
            listener.run();
        }
    }

    private boolean forEachCharacter(String action, java.util.function.Function<Long, Void> work) {
        List<EveCharacter> characters = characterService.listCharacters();
        boolean allSucceeded = true;
        for (EveCharacter character : characters) {
            try {
                work.apply(character.characterId());
            } catch (Exception e) {
                allSucceeded = false;
                LOG.log(Level.WARNING, "Failed to " + action + " for character " + character.characterId(), e);
            }
        }
        return allSucceeded;
    }
}
