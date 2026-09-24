package com.evefarm.service;

import com.evefarm.auth.AuthService;
import com.evefarm.auth.CharacterIdentity;
import com.evefarm.db.dao.CharacterDao;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.model.EveCharacter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class CharacterService {

    private final AuthService authService;
    private final CharacterDao characterDao;
    private final SettingsDao settingsDao;

    public CharacterService(AuthService authService, CharacterDao characterDao, SettingsDao settingsDao) {
        this.authService = authService;
        this.characterDao = characterDao;
        this.settingsDao = settingsDao;
    }

    public CharacterIdentity addCharacter() throws Exception {
        return authService.startLoginFlow();
    }

    public void removeCharacter(long characterId) {
        characterDao.remove(characterId);
        if (mainCharacterId().filter(main -> main == characterId).isPresent()) {
            settingsDao.set(SettingsDao.MAIN_CHARACTER_ID, "");
        }
    }

    public List<EveCharacter> listCharacters() {
        return characterDao.listAll();
    }

    public List<EveCharacter> listCharactersMainFirst() {
        return mainFirst(listCharacters(), mainCharacterId().orElse(null));
    }

    public Optional<Long> mainCharacterId() {
        try {
            return settingsDao.get(SettingsDao.MAIN_CHARACTER_ID)
                    .filter(value -> !value.isBlank())
                    .map(Long::parseLong);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public void setMainCharacter(long characterId) {
        settingsDao.set(SettingsDao.MAIN_CHARACTER_ID, String.valueOf(characterId));
    }

    static List<EveCharacter> mainFirst(List<EveCharacter> characters, Long mainCharacterId) {
        if (mainCharacterId == null) {
            return characters;
        }
        List<EveCharacter> ordered = new ArrayList<>(characters);
        ordered.sort(Comparator.comparing(character -> character.characterId() != mainCharacterId));
        return ordered;
    }
}
