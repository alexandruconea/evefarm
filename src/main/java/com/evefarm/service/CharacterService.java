package com.evefarm.service;

import com.evefarm.auth.AuthService;
import com.evefarm.auth.CharacterIdentity;
import com.evefarm.db.dao.CharacterDao;
import com.evefarm.model.EveCharacter;

import java.util.List;

public final class CharacterService {

    private final AuthService authService;
    private final CharacterDao characterDao;

    public CharacterService(AuthService authService, CharacterDao characterDao) {
        this.authService = authService;
        this.characterDao = characterDao;
    }

    public CharacterIdentity addCharacter() throws Exception {
        return authService.startLoginFlow();
    }

    public void removeCharacter(long characterId) {
        characterDao.remove(characterId);
    }

    public List<EveCharacter> listCharacters() {
        return characterDao.listAll();
    }
}
