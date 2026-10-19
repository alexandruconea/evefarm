package com.evefarm.ui;

import com.evefarm.model.EveCharacter;

import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JComboBox;
import javax.swing.JList;
import java.awt.Component;
import java.util.List;
import java.util.function.Supplier;

final class MainCharacterMarks {

    private MainCharacterMarks() {
    }

    static Icon iconFor(Long mainCharacterId, long characterId) {
        if (mainCharacterId == null) {
            return null;
        }
        return mainCharacterId == characterId ? Icons.MAIN : Icons.BLANK;
    }

    static String mainName(List<EveCharacter> characters, Long mainCharacterId) {
        return characters.stream()
                .filter(character -> mainCharacterId != null && character.characterId() == mainCharacterId)
                .map(EveCharacter::characterName)
                .findFirst().orElse(null);
    }

    static void fill(JComboBox<String> combo, List<EveCharacter> characters, String keepSelected) {
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        characters.forEach(character -> model.addElement(character.characterName()));
        combo.setModel(model);
        if (keepSelected != null && model.getIndexOf(keepSelected) >= 0) {
            combo.setSelectedItem(keepSelected);
        } else if (model.getSize() > 0) {
            combo.setSelectedIndex(0);
        }
    }

    static DefaultListCellRenderer comboRenderer(Supplier<String> mainCharacterName) {
        return new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                String mainName = mainCharacterName.get();
                setIcon(mainName == null ? null : mainName.equals(value) ? Icons.MAIN : Icons.BLANK);
                return this;
            }
        };
    }
}
