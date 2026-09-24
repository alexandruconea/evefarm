package com.evefarm.service;

import com.evefarm.db.dao.TypeCacheDao;
import com.evefarm.esi.UniverseApi;
import com.evefarm.esi.dto.UniverseCategoryDto;
import com.evefarm.esi.dto.UniverseGroupDto;
import com.evefarm.esi.dto.UniverseTypeDto;
import com.evefarm.model.TypeInfo;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class TypeNameCacheService {

    private static final Logger LOG = Logger.getLogger(TypeNameCacheService.class.getName());
    private static final int CONCURRENCY = 10;

    private final UniverseApi universeApi;
    private final TypeCacheDao typeCacheDao;
    private final Map<Integer, UniverseGroupDto> groupCache = new ConcurrentHashMap<>();
    private final Map<Integer, UniverseCategoryDto> categoryCache = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY, r -> {
        Thread t = new Thread(r, "type-resolve");
        t.setDaemon(true);
        return t;
    });

    public TypeNameCacheService(UniverseApi universeApi, TypeCacheDao typeCacheDao) {
        this.universeApi = universeApi;
        this.typeCacheDao = typeCacheDao;
    }

    public TypeInfo resolveType(int typeId) {
        return typeCacheDao.find(typeId).orElseGet(() -> fetchAndCache(typeId));
    }

    public void resolveTypes(Collection<Integer> typeIds) {
        Set<Integer> cached = typeCacheDao.findExistingIds(typeIds);
        List<Integer> missing = typeIds.stream()
                .distinct()
                .filter(id -> !cached.contains(id))
                .toList();
        if (missing.isEmpty()) {
            return;
        }
        List<CompletableFuture<Void>> futures = missing.stream()
                .map(id -> CompletableFuture.runAsync(() -> fetchAndCache(id), executor))
                .toList();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0])).join();
    }

    private TypeInfo fetchAndCache(int typeId) {
        try {
            UniverseTypeDto type = universeApi.getType(typeId);
            UniverseGroupDto group = groupCache.computeIfAbsent(type.groupId(), universeApi::getGroup);
            UniverseCategoryDto category = categoryCache.computeIfAbsent(group.categoryId(), universeApi::getCategory);

            TypeInfo info = new TypeInfo(
                    type.typeId(), type.name(), group.name(), category.name(),
                    type.volume() == null ? 0 : type.volume());
            typeCacheDao.upsert(info, group.groupId(), category.categoryId());
            return info;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to resolve type " + typeId, e);
            return new TypeInfo(typeId, "Type #" + typeId, null, null, 0);
        }
    }
}
