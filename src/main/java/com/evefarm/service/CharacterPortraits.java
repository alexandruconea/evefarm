package com.evefarm.service;

import com.evefarm.service.ItemIconService.EveImage;

import javax.swing.Icon;

public final class CharacterPortraits {

    private final CharacterService characterService;
    private final ItemIconService icons;

    public CharacterPortraits(CharacterService characterService, ItemIconService icons) {
        this.characterService = characterService;
        this.icons = icons;
    }

    public void reload(Runnable onLoaded) {
        characterService.listCharacters().forEach(character ->
                icons.loadImageAsync(EveImage.portrait(character.characterId()), onLoaded));
    }

    public Icon portrait(long characterId, int size) {
        return icons.liveImage(EveImage.portrait(characterId), size);
    }
}
