package com.evefarm.service;

import com.evefarm.db.dao.CharacterDao;
import com.evefarm.model.EveCharacter;

final class CharacterScopes {

    static EveCharacter require(CharacterDao characterDao, long characterId, String scope, String purpose) {
        EveCharacter character = characterDao.listAll().stream()
                .filter(candidate -> candidate.characterId() == characterId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Character " + characterId + " isn't added"));
        if (character.scopes() == null || !character.scopes().contains(scope)) {
            throw new IllegalStateException("Add " + character.characterName() + " again (File > Characters... > "
                    + "Add Character...) so EVE Farm may read its " + purpose + ".");
        }
        return character;
    }

    private CharacterScopes() {
    }
}
