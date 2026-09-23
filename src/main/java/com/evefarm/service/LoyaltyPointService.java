package com.evefarm.service;

import com.evefarm.auth.AuthService;
import com.evefarm.db.dao.EntityNameCacheDao;
import com.evefarm.db.dao.LoyaltyPointDao;
import com.evefarm.esi.LoyaltyApi;
import com.evefarm.esi.dto.LoyaltyPointDto;
import com.evefarm.model.LoyaltyPointEntry;
import com.evefarm.model.LoyaltyPointRow;
import com.evefarm.model.NpcCorporationRow;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class LoyaltyPointService {

    private final AuthService authService;
    private final LoyaltyApi loyaltyApi;
    private final EntityNameCacheService entityNameCacheService;
    private final EntityNameCacheDao entityNameCacheDao;
    private final LoyaltyPointDao loyaltyPointDao;

    public LoyaltyPointService(AuthService authService, LoyaltyApi loyaltyApi,
                                EntityNameCacheService entityNameCacheService, EntityNameCacheDao entityNameCacheDao,
                                LoyaltyPointDao loyaltyPointDao) {
        this.authService = authService;
        this.loyaltyApi = loyaltyApi;
        this.entityNameCacheService = entityNameCacheService;
        this.entityNameCacheDao = entityNameCacheDao;
        this.loyaltyPointDao = loyaltyPointDao;
    }

    public void refreshLoyaltyPointsForCharacter(long characterId) {
        String accessToken = authService.getValidAccessToken(characterId);
        List<LoyaltyPointDto> points = loyaltyApi.listLoyaltyPoints(characterId, accessToken);

        Set<Long> corporationIds = new HashSet<>();
        for (LoyaltyPointDto point : points) {
            corporationIds.add(point.corporationId());
        }
        entityNameCacheService.resolveEntities(corporationIds);

        List<LoyaltyPointEntry> entries = points.stream()
                .map(point -> new LoyaltyPointEntry(point.corporationId(), point.loyaltyPoints()))
                .toList();
        loyaltyPointDao.replaceForCharacter(characterId, entries);
    }

    public List<LoyaltyPointRow> getLoyaltyPoints(long characterId) {
        return loyaltyPointDao.listForCharacter(characterId);
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
