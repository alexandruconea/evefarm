package com.evefarm.ui;

import javax.swing.JComponent;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;

public final class UnderlineTabbedPaneUI extends BasicTabbedPaneUI {

    private static final int UNDERLINE_HEIGHT = 3;
    private static final Color FALLBACK_ACCENT = new Color(0x2D6FD6);

    public static ComponentUI createUI(JComponent c) {
        return new UnderlineTabbedPaneUI();
    }

    public static void install() {
        UIManager.put("TabbedPaneUI", UnderlineTabbedPaneUI.class.getName());
    }

    @Override
    protected void installDefaults() {
        super.installDefaults();
        tabInsets = new Insets(7, 12, 7, 12);
        selectedTabPadInsets = new Insets(0, 0, 0, 0);
        tabAreaInsets = new Insets(2, 4, 0, 4);
        contentBorderInsets = new Insets(1, 0, 0, 0);
        tabRunOverlay = 0;
        textIconGap = 6;
    }

    private static Color accent() {
        Color color = UIManager.getColor("Component.accentColor");
        return color == null ? FALLBACK_ACCENT : color;
    }

    private Color separator() {
        Color shadow = UIManager.getColor("controlShadow");
        return shadow == null ? new Color(0xD0D0D0) : shadow;
    }

    @Override
    protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h,
                                      boolean isSelected) {
        Color fill = null;
        if (isSelected) {
            Color accent = accent();
            fill = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 22);
        } else if (tabIndex == getRolloverTab() && tabPane.isEnabledAt(tabIndex)) {
            fill = new Color(0, 0, 0, 14);
        }
        if (fill != null) {
            g.setColor(fill);
            g.fillRect(x, y, w, h);
        }
    }

    @Override
    protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h,
                                  boolean isSelected) {
        if (!isSelected || tabPlacement != SwingConstants.TOP) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(accent());
            g2.fillRoundRect(x + 2, y + h - UNDERLINE_HEIGHT, w - 4, UNDERLINE_HEIGHT, UNDERLINE_HEIGHT,
                    UNDERLINE_HEIGHT);
        } finally {
            g2.dispose();
        }
    }

    @Override
    protected void paintFocusIndicator(Graphics g, int tabPlacement, Rectangle[] rects, int tabIndex,
                                       Rectangle iconRect, Rectangle textRect, boolean isSelected) {
    }

    @Override
    protected int getTabLabelShiftX(int tabPlacement, int tabIndex, boolean isSelected) {
        return 0;
    }

    @Override
    protected int getTabLabelShiftY(int tabPlacement, int tabIndex, boolean isSelected) {
        return 0;
    }

    @Override
    protected void paintContentBorder(Graphics g, int tabPlacement, int selectedIndex) {
        if (tabPlacement != SwingConstants.TOP) {
            super.paintContentBorder(g, tabPlacement, selectedIndex);
            return;
        }
        Insets insets = tabPane.getInsets();
        int y = insets.top + calculateTabAreaHeight(tabPlacement, runCount, maxTabHeight);
        g.setColor(separator());
        g.fillRect(insets.left, y, tabPane.getWidth() - insets.left - insets.right, 1);
    }
}
