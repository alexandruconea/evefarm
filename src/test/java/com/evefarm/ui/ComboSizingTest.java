package com.evefarm.ui;

import org.junit.jupiter.api.Test;

import javax.swing.JComboBox;
import javax.swing.JTextField;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComboSizingTest {

    @Test
    void everyDropdownHasTheHeightOfTheOneWithTheMainCharacterStar() {
        JComboBox<String> character = new JComboBox<>(new String[]{"Malpais Legate"});
        character.setRenderer(MainCharacterMarks.comboRenderer(() -> "Malpais Legate"));
        int withStar = character.getPreferredSize().height;
        JComboBox<String> tier = new JComboBox<>(new String[]{"T3 Fierce"});
        JTextField notes = new JTextField(20);

        ComboSizing.names(character);
        ComboSizing.options(tier);
        ComboSizing.input(notes);

        assertTrue(ComboSizing.height() >= withStar, "the star must fit");
        assertEquals(ComboSizing.height(), character.getPreferredSize().height);
        assertEquals(ComboSizing.height(), tier.getPreferredSize().height);
        assertEquals(ComboSizing.height(), notes.getPreferredSize().height);
    }

    @Test
    void namesShareOneWidthAndShortListsKeepRoomForTheirText() {
        JComboBox<String> corporation = new JComboBox<>(new String[]{"Corporate Police Force"});
        JComboBox<String> groupBy = new JComboBox<>(new String[]{"Tier", "Filament", "Fleet"});
        int natural = groupBy.getPreferredSize().width;

        ComboSizing.names(corporation);
        ComboSizing.options(groupBy);

        assertEquals(ComboSizing.NAME_WIDTH, corporation.getPreferredSize().width);
        assertEquals(ComboSizing.NAME_WIDTH, corporation.getMaximumSize().width);
        assertEquals(natural + 12, groupBy.getPreferredSize().width);
    }
}
