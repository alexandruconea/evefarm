package com.evefarm.ui;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import java.awt.Dimension;

public final class ComboSizing {

    public static final int NAME_WIDTH = 220;
    private static final int SLACK = 12;
    private static final String PROBE = "Character Name";

    public static void names(JComboBox<?> combo) {
        fix(combo, NAME_WIDTH);
    }

    public static void options(JComboBox<?> combo) {
        fix(combo, combo.getPreferredSize().width + SLACK);
    }

    public static void input(JComponent component) {
        fix(component, component.getPreferredSize().width);
    }

    public static void fix(JComponent component, int width) {
        Dimension size = new Dimension(width, height());
        component.setPreferredSize(size);
        component.setMaximumSize(size);
    }

    public static int height() {
        JComboBox<String> plain = new JComboBox<>(new String[]{PROBE});
        JComboBox<String> marked = new JComboBox<>(new String[]{PROBE});
        marked.setRenderer(MainCharacterMarks.comboRenderer(() -> PROBE));
        return Math.max(plain.getPreferredSize().height, marked.getPreferredSize().height);
    }

    private ComboSizing() {
    }
}
