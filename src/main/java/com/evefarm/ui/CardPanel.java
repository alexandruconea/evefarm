package com.evefarm.ui;

import com.formdev.flatlaf.ui.FlatLineBorder;

import javax.swing.JPanel;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Insets;
import java.awt.LayoutManager;

final class CardPanel extends JPanel {

    CardPanel(LayoutManager layout) {
        super(layout);
        style();
    }

    @Override
    public void updateUI() {
        super.updateUI();
        style();
    }

    private void style() {
        setBackground(UIManager.getColor("Table.background"));
        Color border = UIManager.getColor("Component.borderColor");
        setBorder(new FlatLineBorder(new Insets(4, 14, 4, 14), border == null ? Color.LIGHT_GRAY : border, 1, 12));
    }
}
