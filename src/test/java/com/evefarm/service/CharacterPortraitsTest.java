package com.evefarm.service;

import com.evefarm.model.EveCharacter;
import com.evefarm.service.ItemIconService.EveImage;
import org.junit.jupiter.api.Test;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CharacterPortraitsTest {

    @Test
    void everyCharactersPortraitIsFetched() {
        CharacterService characters = mock(CharacterService.class);
        when(characters.listCharacters()).thenReturn(List.of(
                new EveCharacter(2124165849L, "Malpais Legate", List.of(), Instant.EPOCH),
                new EveCharacter(95465499L, "Barset", List.of(), Instant.EPOCH)));
        ItemIconService icons = mock(ItemIconService.class);
        Icon malpais = new ImageIcon();
        when(icons.liveImage(EveImage.portrait(2124165849L), 32)).thenReturn(malpais);
        Runnable onLoaded = () -> {
        };
        CharacterPortraits portraits = new CharacterPortraits(characters, icons);

        portraits.reload(onLoaded);

        verify(icons).loadImageAsync(EveImage.portrait(2124165849L), onLoaded);
        verify(icons).loadImageAsync(EveImage.portrait(95465499L), onLoaded);
        assertSame(malpais, portraits.portrait(2124165849L, 32));
    }
}
