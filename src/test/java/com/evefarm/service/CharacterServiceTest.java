package com.evefarm.service;

import com.evefarm.auth.AuthService;
import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.db.dao.CharacterDao;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.model.EveCharacter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CharacterServiceTest {

    private static final EveCharacter BARSET = character(1L, "Barset");
    private static final EveCharacter CASTAN = character(2L, "Castan Marian");
    private static final EveCharacter MALPAIS = character(3L, "Malpais Legate");

    private SettingsDao settings;
    private CharacterService service;

    @BeforeEach
    void setUp() {
        Database database = new Database(":memory:");
        MigrationRunner.run(database);
        settings = new SettingsDao(database);
        CharacterDao characters = mock(CharacterDao.class);
        when(characters.listAll()).thenReturn(List.of(BARSET, CASTAN, MALPAIS));
        service = new CharacterService(mock(AuthService.class), characters, settings);
    }

    @Test
    void theMainCharacterIsListedFirstAndTheRestKeepTheirOrder() {
        service.setMainCharacter(MALPAIS.characterId());

        assertEquals(List.of(MALPAIS, BARSET, CASTAN), service.listCharactersMainFirst());
        assertEquals(List.of(BARSET, CASTAN, MALPAIS), service.listCharacters(),
                "the plain list stays alphabetical, e.g. for Update's First Character option");
    }

    @Test
    void withoutAMainTheListStaysAlphabetical() {
        assertEquals(List.of(BARSET, CASTAN, MALPAIS), service.listCharactersMainFirst());
        assertEquals(Optional.empty(), service.mainCharacterId());
    }

    @Test
    void removingTheMainCharacterClearsIt() {
        service.setMainCharacter(MALPAIS.characterId());

        service.removeCharacter(MALPAIS.characterId());

        assertEquals(Optional.empty(), service.mainCharacterId());
    }

    @Test
    void removingAnotherCharacterKeepsTheMain() {
        service.setMainCharacter(MALPAIS.characterId());

        service.removeCharacter(BARSET.characterId());

        assertEquals(Optional.of(MALPAIS.characterId()), service.mainCharacterId());
    }

    @Test
    void aDamagedSettingIsIgnored() {
        settings.set(SettingsDao.MAIN_CHARACTER_ID, "not a number");

        assertEquals(Optional.empty(), service.mainCharacterId());
    }

    private static EveCharacter character(long id, String name) {
        return new EveCharacter(id, name, null, List.of(), Instant.EPOCH, true);
    }
}
