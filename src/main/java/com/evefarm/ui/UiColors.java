package com.evefarm.ui;

import javax.swing.JLabel;
import javax.swing.UIManager;
import java.awt.Color;

final class UiColors {

    static Color muted() {
        Color themed = UIManager.getColor("Label.disabledForeground");
        return themed != null ? themed : Color.GRAY;
    }

    static Color danger() {
        Color themed = UIManager.getColor("Actions.Red");
        return themed != null ? themed : new Color(0xDB5860);
    }

    static JLabel mutedLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(muted());
        return label;
    }

    private UiColors() {
    }
}
