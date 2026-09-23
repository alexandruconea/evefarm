package com.evefarm.service;

import com.evefarm.db.dao.LocationCacheDao;
import com.evefarm.esi.EsiException;
import com.evefarm.esi.UniverseApi;
import com.evefarm.esi.dto.StationDto;
import com.evefarm.esi.dto.StructureDto;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class LocationNameCacheService {

    private static final Logger LOG = Logger.getLogger(LocationNameCacheService.class.getName());
    private static final int CONCURRENCY = 10;

    private static final long STRUCTURE_ID_THRESHOLD = 1_000_000_000_000L;

    private final UniverseApi universeApi;
    private final LocationCacheDao locationCacheDao;
    private final ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY, r -> {
        Thread t = new Thread(r, "location-resolve");
        t.setDaemon(true);
        return t;
    });

    public LocationNameCacheService(UniverseApi universeApi, LocationCacheDao locationCacheDao) {
        this.universeApi = universeApi;
        this.locationCacheDao = locationCacheDao;
    }

    public String resolveLocation(long locationId, String ownerAccessToken) {
        Optional<String> cached = locationCacheDao.findName(locationId);
        if (cached.isPresent()) {
            return cached.get();
        }

        if (locationId >= STRUCTURE_ID_THRESHOLD) {
            return resolveStructure(locationId, ownerAccessToken);
        }
        return resolveStation(locationId, ownerAccessToken);
    }

    public void resolveLocations(Collection<Long> locationIds, String ownerAccessToken) {
        Set<Long> cached = locationCacheDao.findExistingIds(locationIds);
        List<Long> missing = locationIds.stream()
                .distinct()
                .filter(id -> !cached.contains(id))
                .toList();
        if (missing.isEmpty()) {
            return;
        }
        List<CompletableFuture<Void>> futures = missing.stream()
                .map(id -> CompletableFuture.runAsync(() -> resolveLocation(id, ownerAccessToken), executor))
                .toList();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    private String resolveStation(long locationId, String ownerAccessToken) {
        try {
            StationDto station = universeApi.getStation(locationId);
            locationCacheDao.upsert(locationId, station.name(), "station", station.systemId());
            return station.name();
        } catch (EsiException e) {
            if (e.statusCode() == 404) {
                return resolveStructure(locationId, ownerAccessToken);
            }
            LOG.log(Level.WARNING, "Transient failure resolving station " + locationId
                    + " (HTTP " + e.statusCode() + ") - not caching, will retry later", e);
            return "Location #" + locationId;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Transient failure resolving station " + locationId
                    + " - not caching, will retry later", e);
            return "Location #" + locationId;
        }
    }

    private String resolveStructure(long locationId, String ownerAccessToken) {
        try {
            StructureDto structure = universeApi.getStructure(locationId, ownerAccessToken);
            locationCacheDao.upsert(locationId, structure.name(), "structure", structure.solarSystemId());
            return structure.name();
        } catch (EsiException e) {
            if (e.statusCode() == 403 || e.statusCode() == 404) {
                String name = "Unknown Structure #" + locationId;
                locationCacheDao.upsert(locationId, name, "unknown", null);
                return name;
            }
            LOG.log(Level.WARNING, "Transient failure resolving structure " + locationId
                    + " (HTTP " + e.statusCode() + ") - not caching, will retry later", e);
            return "Location #" + locationId;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Transient failure resolving structure " + locationId
                    + " - not caching, will retry later", e);
            return "Location #" + locationId;
        }
    }
}
