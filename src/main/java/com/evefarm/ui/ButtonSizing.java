package com.evefarm.ui;

import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.GridLayout;

public final class ButtonSizing {

    public static JPanel row(int gap, JButton... buttons) {
        JPanel panel = new JPanel(new GridLayout(1, buttons.length, gap, 0));
        panel.setOpaque(false);
        for (JButton button : buttons) {
            panel.add(button);
        }
        return panel;
    }

    public static JPanel column(int gap, JButton... buttons) {
        JPanel panel = new JPanel(new GridLayout(buttons.length, 1, 0, gap));
        panel.setOpaque(false);
        for (JButton button : buttons) {
            panel.add(button);
        }
        return panel;
    }

    private ButtonSizing() {
    }
}
