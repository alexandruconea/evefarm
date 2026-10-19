package com.evefarm.ui;

import com.evefarm.model.EveCharacter;
import org.junit.jupiter.api.Test;

import javax.swing.JComboBox;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MainCharacterMarksTest {

    private static final EveCharacter MAIN = new EveCharacter(1L, "Malpais Legate", List.of(), Instant.EPOCH);
    private static final EveCharacter ALT = new EveCharacter(2L, "Castan Marian", List.of(), Instant.EPOCH);

    @Test
    void theMainCharactersNameIsFoundOnlyWhenOneIsChosen() {
        assertEquals("Malpais Legate", MainCharacterMarks.mainName(List.of(ALT, MAIN), 1L));
        assertNull(MainCharacterMarks.mainName(List.of(ALT, MAIN), null));
        assertNull(MainCharacterMarks.mainName(List.of(ALT), 1L));
    }

    @Test
    void refillingKeepsTheChosenCharacterOrStartsAtTheFirst() {
        JComboBox<String> combo = new JComboBox<>();

        MainCharacterMarks.fill(combo, List.of(MAIN, ALT), null);
        assertEquals("Malpais Legate", combo.getSelectedItem());

        MainCharacterMarks.fill(combo, List.of(MAIN, ALT), "Castan Marian");
        assertEquals("Castan Marian", combo.getSelectedItem());

        MainCharacterMarks.fill(combo, List.of(MAIN), "Castan Marian");
        assertEquals("Malpais Legate", combo.getSelectedItem());
        assertEquals(1, combo.getItemCount());
    }
}
