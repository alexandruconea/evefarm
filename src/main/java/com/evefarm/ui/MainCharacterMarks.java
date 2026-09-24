package com.evefarm.ui;

import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JList;
import java.awt.Component;
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
