package com.evefarm.service;

import com.evefarm.db.dao.EntityNameCacheDao;
import com.evefarm.esi.UniverseApi;
import com.evefarm.esi.dto.UniverseNameDto;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class EntityNameCacheService {

    private static final Logger LOG = Logger.getLogger(EntityNameCacheService.class.getName());

    private final UniverseApi universeApi;
    private final EntityNameCacheDao entityNameCacheDao;

    public EntityNameCacheService(UniverseApi universeApi, EntityNameCacheDao entityNameCacheDao) {
        this.universeApi = universeApi;
        this.entityNameCacheDao = entityNameCacheDao;
    }

    public void resolveEntities(Collection<Long> entityIds) {
        List<Long> positiveIds = entityIds.stream().filter(id -> id > 0).toList();
        Set<Long> cached = entityNameCacheDao.findExistingIds(positiveIds);
        List<Long> missing = positiveIds.stream()
                .distinct()
                .filter(id -> !cached.contains(id))
                .toList();
        if (missing.isEmpty()) {
            return;
        }
        try {
            List<UniverseNameDto> resolved = universeApi.resolveNames(missing);
            Set<Long> resolvedIds = resolved.stream().map(UniverseNameDto::id).collect(Collectors.toSet());
            for (UniverseNameDto dto : resolved) {
                entityNameCacheDao.upsert(dto.id(), dto.name(), dto.category());
            }
            for (Long id : missing) {
                if (!resolvedIds.contains(id)) {
                    entityNameCacheDao.upsert(id, "#" + id, "unknown");
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to resolve entity names", e);
        }
    }
}
