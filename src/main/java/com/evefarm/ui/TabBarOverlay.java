package com.evefarm.ui;

import com.formdev.flatlaf.FlatLaf;

import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JTabbedPane;
import javax.swing.UIManager;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;

final class TabBarOverlay {

    private static final int MARGIN = 8;

    static JComponent wrap(JTabbedPane tabs, JComponent trailing) {
        int space = trailing.getPreferredSize().width + 2 * MARGIN;
        tabs.putClientProperty(UnderlineTabbedPaneUI.TRAILING_SPACE, space);
        Insets flat = UIManager.getInsets("TabbedPane.tabAreaInsets");
        if (UIManager.getLookAndFeel() instanceof FlatLaf && flat != null) {
            tabs.putClientProperty("JTabbedPane.tabAreaInsets",
                    new Insets(flat.top, flat.left, flat.bottom, flat.right + space));
        }
        JLayeredPane layers = new JLayeredPane();
        layers.setLayout(new Layout(tabs, trailing));
        layers.add(tabs, JLayeredPane.DEFAULT_LAYER);
        layers.add(trailing, JLayeredPane.PALETTE_LAYER);
        return layers;
    }

    private record Layout(JTabbedPane tabs, JComponent trailing) implements LayoutManager {

        @Override
        public void addLayoutComponent(String name, Component component) {
        }

        @Override
        public void removeLayoutComponent(Component component) {
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            return tabs.getPreferredSize();
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return tabs.getMinimumSize();
        }

        @Override
        public void layoutContainer(Container parent) {
            tabs.setBounds(0, 0, parent.getWidth(), parent.getHeight());
            Dimension size = trailing.getPreferredSize();
            int top = MARGIN / 2;
            int height = size.height;
            for (int i = 0; i < tabs.getTabCount(); i++) {
                Rectangle bounds = tabs.getBoundsAt(i);
                if (bounds != null && (i == 0 || bounds.y < top)) {
                    top = bounds.y;
                    height = bounds.height;
                }
            }
            trailing.setBounds(parent.getWidth() - size.width - MARGIN, top + (height - size.height) / 2,
                    size.width, size.height);
        }
    }

    private TabBarOverlay() {
    }
}
