package com.evefarm.service;

import com.evefarm.db.dao.LocationCacheDao;
import com.evefarm.esi.EsiException;
import com.evefarm.esi.UniverseApi;
import com.evefarm.esi.dto.StationDto;
import com.evefarm.esi.dto.StructureDto;
import com.evefarm.esi.dto.UniverseNameDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocationNameCacheServiceTest {

    private UniverseApi universe;
    private LocationCacheDao cache;
    private LocationNameCacheService service;

    @BeforeEach
    void setUp() {
        universe = mock(UniverseApi.class);
        cache = mock(LocationCacheDao.class);
        when(cache.findName(anyLong())).thenReturn(Optional.empty());
        service = new LocationNameCacheService(universe, cache);
    }

    @Test
    void itemsInSpaceAreNamedAfterTheirSolarSystem() {
        when(universe.resolveNames(List.of(30000142L)))
                .thenReturn(List.of(new UniverseNameDto(30000142L, "Jita", "solar_system")));

        assertEquals("Jita", service.resolveLocation(30000142L, "token"));

        verify(cache).upsert(30000142L, "Jita", "solar_system", 30000142L);
        verify(universe, never()).getStation(anyLong());
        verify(universe, never()).getStructure(anyLong(), anyString());
    }

    @Test
    void stationsUseTheStationEndpoint() {
        when(universe.getStation(60003760L)).thenReturn(new StationDto(60003760L, "Jita IV - Moon 4", 30000142L));

        assertEquals("Jita IV - Moon 4", service.resolveLocation(60003760L, "token"));

        verify(cache).upsert(60003760L, "Jita IV - Moon 4", "station", 30000142L);
    }

    @Test
    void structuresUseTheAuthenticatedStructureEndpoint() {
        when(universe.getStructure(1_035_466_617_946L, "token"))
                .thenReturn(new StructureDto("Keepstar", 1L, 30000142L, 35834));

        assertEquals("Keepstar", service.resolveLocation(1_035_466_617_946L, "token"));

        verify(cache).upsert(1_035_466_617_946L, "Keepstar", "structure", 30000142L);
    }

    @Test
    void assetSafetyNeedsNoLookup() {
        assertEquals("Asset Safety", service.resolveLocation(2004L, "token"));

        verify(cache).upsert(2004L, "Asset Safety", "other", null);
        verify(universe, never()).getStation(anyLong());
        verify(universe, never()).resolveNames(any());
    }

    @Test
    void anIdEsiRejectsIsRememberedInsteadOfRetriedForever() {
        when(universe.getStation(40009082L)).thenThrow(new EsiException(400, "not a station"));

        assertEquals("Unknown Location #40009082", service.resolveLocation(40009082L, "token"));

        verify(cache).upsert(40009082L, "Unknown Location #40009082", "unknown", null);
        verify(universe, never()).getStructure(anyLong(), anyString());
    }

    @Test
    void aServerErrorIsRetriedLater() {
        when(universe.resolveNames(List.of(30000142L))).thenThrow(new EsiException(502, "bad gateway"));

        assertEquals("Location #30000142", service.resolveLocation(30000142L, "token"));

        verify(cache, never()).upsert(anyLong(), anyString(), anyString(), any());
    }
}
