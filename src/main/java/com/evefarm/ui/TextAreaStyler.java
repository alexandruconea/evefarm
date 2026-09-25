package com.evefarm.ui;

import javax.swing.BorderFactory;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;

final class TextAreaStyler {

    static void informational(JTextArea area) {
        common(area);
        area.setFocusable(false);
        area.setOpaque(false);
        area.setBorder(BorderFactory.createEmptyBorder());
    }

    static void scrollable(JTextArea area) {
        common(area);
        area.setMargin(new Insets(6, 8, 6, 8));
    }

    private static void common(JTextArea area) {
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        Font font = UIManager.getFont("Label.font");
        if (font != null) {
            area.setFont(font);
        }
        Color foreground = UIManager.getColor("Label.foreground");
        if (foreground != null) {
            area.setForeground(foreground);
        }
    }

    private TextAreaStyler() {
    }
}
