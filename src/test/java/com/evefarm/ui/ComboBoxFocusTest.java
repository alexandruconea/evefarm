package com.evefarm.ui;

import org.junit.jupiter.api.Test;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JList;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ComboBoxFocusTest {

    private static final class Recording extends DefaultListCellRenderer {
        final List<String> calls = new ArrayList<>();

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
                                                      boolean cellHasFocus) {
            calls.add(index + ":" + isSelected + ":" + cellHasFocus);
            return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        }
    }

    @Test
    void theValueShownInTheBoxIsNeverDrawnAsFocused() {
        Recording recording = new Recording();
        JComboBox<String> combo = new JComboBox<>(new String[]{"Windows", "Dark", "EVE"});
        combo.setRenderer(recording);

        ComboBoxFocus.plainCurrentValue(combo);
        combo.getRenderer().getListCellRendererComponent(new JList<>(), "Windows", -1, true, true);
        combo.getRenderer().getListCellRendererComponent(new JList<>(), "Dark", 1, true, true);

        assertEquals(List.of("-1:false:false", "1:true:true"), recording.calls,
                "the open list still shows the selected line");
    }

    @Test
    void aComboIsWrappedOnlyOnce() {
        JComboBox<String> combo = new JComboBox<>(new String[]{"Midpoint"});
        ComboBoxFocus.plainCurrentValue(combo);
        Object wrapped = combo.getRenderer();

        ComboBoxFocus.plainCurrentValue(combo);

        assertSame(wrapped, combo.getRenderer());
    }
}
