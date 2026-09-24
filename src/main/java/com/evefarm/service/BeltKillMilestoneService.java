package com.evefarm.service;

import com.evefarm.db.dao.KillDao;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.model.SpawnClass;

import java.util.Map;
import java.util.OptionalLong;

public final class BeltKillMilestoneService {

    public static final long STEP = 1000;

    private final KillDao killDao;
    private final SettingsDao settingsDao;
    private final NpcCatalogService npcCatalogService;

    public BeltKillMilestoneService(KillDao killDao, SettingsDao settingsDao, NpcCatalogService npcCatalogService) {
        this.killDao = killDao;
        this.settingsDao = settingsDao;
        this.npcCatalogService = npcCatalogService;
    }

    public long countBeltKills() {
        NpcCatalog catalog = npcCatalogService.catalog();
        long total = 0;
        for (Map.Entry<String, Long> entry : killDao.countKillsByNpcName().entrySet()) {
            if (catalog.spawnClassOf(entry.getKey()) != SpawnClass.OTHER) {
                total += entry.getValue();
            }
        }
        return total;
    }

    public synchronized OptionalLong checkForNewMilestone() {
        if (npcCatalogService.catalog().isEmpty()) {
            return OptionalLong.empty();
        }
        long reached = countBeltKills() / STEP * STEP;
        Long announced = lastAnnounced();
        if (announced == null || reached > announced) {
            settingsDao.set(SettingsDao.BELT_KILL_MILESTONE, String.valueOf(reached));
        }
        if (announced != null && reached > announced && reached > 0) {
            return OptionalLong.of(reached);
        }
        return OptionalLong.empty();
    }

    private Long lastAnnounced() {
        try {
            return settingsDao.get(SettingsDao.BELT_KILL_MILESTONE).map(Long::parseLong).orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
