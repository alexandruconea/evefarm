package com.evefarm.ui;

import javax.swing.ButtonModel;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.UIManager;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.InsetsUIResource;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicMenuItemUI;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

public final class CompactMenuItemUI extends BasicMenuItemUI {

    private static final Color HIGHLIGHT = new Color(0, 0, 0, 18);
    private static final Color PRESSED = new Color(0, 0, 0, 30);

    public static ComponentUI createUI(JComponent component) {
        return new CompactMenuItemUI();
    }

    public static void install() {
        UIManager.put("MenuItemUI", CompactMenuItemUI.class.getName());
        UIManager.put("MenuItem.minimumTextOffset", 0);
        UIManager.put("MenuItem.afterCheckIconGap", 0);
        UIManager.put("MenuItem.checkIconOffset", 0);
        UIManager.put("MenuItem.checkIconFactory", null);
    }

    @Override
    protected void installDefaults() {
        super.installDefaults();
        checkIcon = null;
        defaultTextIconGap = 8;
        selectionForeground = menuItem.getForeground();
        if (menuItem.getMargin() == null || menuItem.getMargin() instanceof UIResource) {
            menuItem.setMargin(new InsetsUIResource(5, 6, 5, 18));
        }
    }

    @Override
    protected void paintBackground(Graphics g, JMenuItem item, Color background) {
        Graphics2D g2 = (Graphics2D) g.create();
        if (item.isOpaque()) {
            g2.setColor(item.getBackground());
            g2.fillRect(0, 0, item.getWidth(), item.getHeight());
        }
        ButtonModel model = item.getModel();
        if (model.isArmed()) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(model.isPressed() ? PRESSED : HIGHLIGHT);
            g2.fillRoundRect(4, 1, item.getWidth() - 8, item.getHeight() - 2, 8, 8);
        }
        g2.dispose();
    }
}
