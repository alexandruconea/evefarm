package com.evefarm.service;

import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.db.dao.EntityNameCacheDao;
import com.evefarm.esi.EsiException;
import com.evefarm.esi.UniverseApi;
import com.evefarm.esi.dto.UniverseNameDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EntityNameCacheServiceTest {

    private static final long MALPAIS = 2124165849L;
    private static final long CALDARI_NAVY = 1000035L;
    private static final long NOT_AN_ENTITY = 1051964920941L;

    @Test
    void anIdEveCantNameDoesntCostTheOthersTheirNames() {
        Database database = new Database(":memory:");
        MigrationRunner.run(database);
        UniverseApi universe = mock(UniverseApi.class);
        when(universe.resolveNames(anyList())).thenAnswer(call -> {
            List<Long> ids = call.getArgument(0);
            if (ids.contains(NOT_AN_ENTITY)) {
                throw new EsiException(404, "{\"error\":\"Ensure all IDs are valid before resolving\"}");
            }
            return ids.stream().map(id -> new UniverseNameDto(id, id == MALPAIS ? "Malpais Legate" : "Caldari Navy",
                    id == MALPAIS ? "character" : "corporation")).toList();
        });
        EntityNameCacheService names = new EntityNameCacheService(universe, new EntityNameCacheDao(database));

        Map<Long, String> resolved = names.resolveEntityNames(List.of(MALPAIS, NOT_AN_ENTITY, CALDARI_NAVY));

        assertEquals(Map.of(MALPAIS, "Malpais Legate", CALDARI_NAVY, "Caldari Navy",
                NOT_AN_ENTITY, "#" + NOT_AN_ENTITY), resolved);
    }
}
