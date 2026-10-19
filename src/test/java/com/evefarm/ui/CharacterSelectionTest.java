package com.evefarm.ui;

import com.evefarm.model.EveCharacter;
import com.evefarm.service.CharacterService;
import org.junit.jupiter.api.Test;

import javax.swing.JCheckBox;
import javax.swing.JList;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CharacterSelectionTest {

    private static final EveCharacter MAIN = new EveCharacter(1L, "Malpais Legate", List.of(), Instant.EPOCH);
    private static final EveCharacter ALT = new EveCharacter(2L, "Castan Marian", List.of(), Instant.EPOCH);

    @Test
    void allProfilesCountsEveryoneAndUntickingItUsesTheChosenCharacters() {
        CharacterService characters = mock(CharacterService.class);
        when(characters.listCharactersMainFirst()).thenReturn(List.of(MAIN, ALT));
        when(characters.listCharacters()).thenReturn(List.of(ALT, MAIN));
        when(characters.mainCharacterId()).thenReturn(Optional.of(1L));
        JCheckBox allProfiles = new JCheckBox("All Profiles", true);
        JList<EveCharacter> list = new JList<>();
        int[] changes = {0};
        CharacterSelection selection = new CharacterSelection(characters, allProfiles, list, () -> changes[0]++);

        selection.reload();

        assertEquals(List.of(MAIN, ALT), list.getSelectedValuesList());
        assertEquals(Set.of(1L, 2L), selection.selectedIds());
        assertEquals("All Profiles", selection.label());
        assertFalse(list.isEnabled());

        allProfiles.doClick();
        list.setSelectedIndex(1);

        assertTrue(list.isEnabled());
        assertEquals(Set.of(2L), selection.selectedIds());
        assertEquals("Castan Marian", selection.label());
        list.clearSelection();
        assertEquals("No characters selected", selection.label());
        assertTrue(changes[0] >= 2, "each change reloads the panel");
    }
}
