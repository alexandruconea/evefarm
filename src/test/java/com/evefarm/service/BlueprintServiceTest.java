package com.evefarm.service;

import com.evefarm.auth.AuthService;
import com.evefarm.auth.OAuthConfig;
import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.db.dao.AssetDao;
import com.evefarm.db.dao.BlueprintDao;
import com.evefarm.db.dao.CharacterDao;
import com.evefarm.esi.IndustryApi;
import com.evefarm.esi.dto.BlueprintDto;
import com.evefarm.model.AssetEntry;
import com.evefarm.model.BlueprintRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlueprintServiceTest {

    private static final long JITA = 60003760L;

    private final AuthService auth = mock(AuthService.class);
    private final IndustryApi api = mock(IndustryApi.class);
    private Database database;
    private CharacterDao characters;
    private BlueprintService service;

    @BeforeEach
    void setUp() {
        database = new Database(":memory:");
        MigrationRunner.run(database);
        characters = new CharacterDao(database);
        characters.upsert(1, "Builder", OAuthConfig.SCOPES, "a");
        characters.upsert(2, "Old Login", OAuthConfig.SCOPES.stream()
                .filter(scope -> !scope.equals(OAuthConfig.BLUEPRINTS_SCOPE)).toList(), "b");
        service = new BlueprintService(auth, characters, api, mock(TypeNameCacheService.class),
                new BlueprintDao(database));
        when(auth.getValidAccessToken(anyLong())).thenReturn("token");
    }

    @Test
    void aCharacterAddedBeforeBlueprintsWereReadMustBeAddedAgain() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> service.refreshBlueprintsForCharacter(2));

        assertTrue(failure.getMessage().startsWith("Add Old Login again"));
        verify(api, never()).listBlueprints(anyLong(), anyString());
    }

    @Test
    void blueprintsShowWhereTheirAssetIs() {
        new AssetDao(database).replaceForCharacter(1, List.of(
                new AssetEntry(11, 1_000, 1, JITA, "Unlocked", true, false, null, "Blueprint Box", 0, 0)));
        when(api.listBlueprints(1, "token")).thenReturn(List.of(
                new BlueprintDto(11, 1_000, 99L, "Unlocked", -1, 10, 20, -1),
                new BlueprintDto(12, 1_001, JITA, "Hangar", -2, 0, 0, 3)));

        service.refreshBlueprintsForCharacter(1);

        List<BlueprintRow> rows = service.getBlueprintRows();
        BlueprintRow original = rows.stream().filter(row -> row.itemId() == 11).findFirst().orElseThrow();
        BlueprintRow copy = rows.stream().filter(row -> row.itemId() == 12).findFirst().orElseThrow();
        assertEquals("Builder", original.ownerName());
        assertFalse(original.corporation());
        assertEquals("Blueprint Box", original.containerName());
        assertEquals("Location #" + JITA, original.locationName(), "the station of the box");
        assertEquals("Location #" + JITA, copy.locationName());
        assertTrue(copy.copy());
    }

    @Test
    void aRemovedCharacterTakesItsBlueprintsAlong() {
        when(api.listBlueprints(1, "token")).thenReturn(List.of(
                new BlueprintDto(12, 1_001, JITA, "Hangar", -2, 0, 0, 3)));
        service.refreshBlueprintsForCharacter(1);

        characters.remove(1);

        assertEquals(List.of(), service.getBlueprintRows());
    }
}
