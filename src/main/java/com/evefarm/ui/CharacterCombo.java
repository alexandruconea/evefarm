package com.evefarm.ui;

import com.evefarm.model.EveCharacter;
import com.evefarm.service.CharacterService;

import javax.swing.JComboBox;
import java.util.List;
import java.util.Optional;

final class CharacterCombo extends JComboBox<String> {

    private final transient CharacterService characterService;
    private transient List<EveCharacter> characters = List.of();
    private String mainCharacterName;

    CharacterCombo(CharacterService characterService) {
        this.characterService = characterService;
        setRenderer(MainCharacterMarks.comboRenderer(() -> mainCharacterName));
        ComboSizing.names(this);
    }

    void reload(boolean selectMain) {
        reload(selectMain ? null : selectedName());
    }

    void reload(String keepSelected) {
        characters = characterService.listCharactersMainFirst();
        mainCharacterName = MainCharacterMarks.mainName(characters, characterService.mainCharacterId().orElse(null));
        MainCharacterMarks.fill(this, characters, keepSelected);
    }

    List<EveCharacter> characters() {
        return characters;
    }

    String selectedName() {
        return (String) getSelectedItem();
    }

    EveCharacter selectedCharacter() {
        Object name = getSelectedItem();
        return characters.stream().filter(character -> character.characterName().equals(name))
                .findFirst().orElse(null);
    }

    Optional<EveCharacter> find(long characterId) {
        return characters.stream().filter(character -> character.characterId() == characterId).findFirst();
    }
}
