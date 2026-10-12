package com.evefarm.service;

import com.evefarm.auth.AuthService;
import com.evefarm.db.dao.EntityNameCacheDao;
import com.evefarm.db.dao.LoyaltyPointDao;
import com.evefarm.esi.LoyaltyApi;
import com.evefarm.esi.dto.LoyaltyPointDto;
import com.evefarm.model.LoyaltyPointCheck;
import com.evefarm.model.LoyaltyPointEntry;
import com.evefarm.model.LoyaltyPointHistoryRow;
import com.evefarm.model.LoyaltyPointRow;
import com.evefarm.model.LpGain;
import com.evefarm.model.NpcCorporationRow;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public final class LoyaltyPointService {

    static final Duration EVE_CACHE = Duration.ofHours(1);
    static final Duration AFTER_EVE_REFRESHES = Duration.ofSeconds(30);

    private final AuthService authService;
    private final LoyaltyApi loyaltyApi;
    private final EntityNameCacheService entityNameCacheService;
    private final EntityNameCacheDao entityNameCacheDao;
    private final LoyaltyPointDao loyaltyPointDao;
    private final Clock clock;
    private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();

    public LoyaltyPointService(AuthService authService, LoyaltyApi loyaltyApi,
                                EntityNameCacheService entityNameCacheService, EntityNameCacheDao entityNameCacheDao,
                                LoyaltyPointDao loyaltyPointDao) {
        this(authService, loyaltyApi, entityNameCacheService, entityNameCacheDao, loyaltyPointDao, Clock.systemUTC());
    }

    LoyaltyPointService(AuthService authService, LoyaltyApi loyaltyApi, EntityNameCacheService entityNameCacheService,
                        EntityNameCacheDao entityNameCacheDao, LoyaltyPointDao loyaltyPointDao, Clock clock) {
        this.authService = authService;
        this.loyaltyApi = loyaltyApi;
        this.entityNameCacheService = entityNameCacheService;
        this.entityNameCacheDao = entityNameCacheDao;
        this.loyaltyPointDao = loyaltyPointDao;
        this.clock = clock;
    }

    public synchronized boolean refreshLoyaltyPointsForCharacter(long characterId) {
        LoyaltyPointCheck check = loyaltyPointDao.checks().get(characterId);
        if (check != null && check.nextRead() != null && check.nextRead().isAfter(clock.instant())) {
            return false;
        }
        String accessToken = authService.getValidAccessToken(characterId);
        LoyaltyApi.LoyaltyPoints read = loyaltyApi.readLoyaltyPoints(characterId, accessToken);
        Instant readAt = clock.instant();
        List<LoyaltyPointDto> points = read.points();

        Set<Long> corporationIds = new HashSet<>();
        for (LoyaltyPointDto point : points) {
            corporationIds.add(point.corporationId());
        }
        entityNameCacheService.resolveEntities(corporationIds);

        List<LoyaltyPointEntry> entries = points.stream()
                .map(point -> new LoyaltyPointEntry(point.corporationId(), point.loyaltyPoints()))
                .toList();
        boolean saved = loyaltyPointDao.replaceForCharacter(characterId, entries, readAt.minus(read.age()),
                nextRead(readAt, read));
        if (saved) {
            for (Runnable listener : changeListeners) {
                listener.run();
            }
        }
        return saved;
    }

    public void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    static Instant nextRead(Instant readAt, LoyaltyApi.LoyaltyPoints read) {
        Duration freshFor = read.freshFor() == null || read.freshFor().compareTo(EVE_CACHE) > 0 ? EVE_CACHE
                : read.freshFor();
        return readAt.plus(freshFor).plus(AFTER_EVE_REFRESHES);
    }

    public Map<Long, LoyaltyPointCheck> checks() {
        return loyaltyPointDao.checks();
    }

    public List<LoyaltyPointRow> getLoyaltyPoints(long characterId) {
        return loyaltyPointDao.listForCharacter(characterId);
    }

    public List<LoyaltyPointHistoryRow> getHistory() {
        return loyaltyPointDao.listHistory();
    }

    public List<LpGain> gains() {
        return loyaltyPointDao.listGains();
    }

    public Map<Long, Instant> firstRecorded() {
        return loyaltyPointDao.firstRecorded();
    }

    public List<NpcCorporationRow> listAllNpcCorporations() {
        List<Long> corporationIds = loyaltyApi.listNpcCorporationIds();
        entityNameCacheService.resolveEntities(new HashSet<>(corporationIds));
        return corporationIds.stream()
                .map(id -> new NpcCorporationRow(id, entityNameCacheDao.findName(id).orElse("Corporation #" + id)))
                .sorted(Comparator.comparing(NpcCorporationRow::corporationName))
                .toList();
    }
}
