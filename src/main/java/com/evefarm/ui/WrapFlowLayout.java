package com.evefarm.ui;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;

final class WrapFlowLayout extends FlowLayout {

    private int requested = -1;

    WrapFlowLayout(int hgap, int vgap) {
        super(LEFT, hgap, vgap);
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        synchronized (target.getTreeLock()) {
            Dimension single = super.preferredLayoutSize(target);
            int width = target.getWidth();
            if (width <= 0 || single.width <= width) {
                return single;
            }
            Insets insets = target.getInsets();
            int maxWidth = width - insets.left - insets.right - getHgap() * 2;
            int x = 0;
            int rowHeight = 0;
            int height = 0;
            int widest = 0;
            for (Component component : target.getComponents()) {
                if (!component.isVisible()) {
                    continue;
                }
                Dimension size = component.getPreferredSize();
                if (x > 0 && x + size.width > maxWidth) {
                    height += rowHeight + getVgap();
                    x = 0;
                    rowHeight = 0;
                }
                x += (x > 0 ? getHgap() : 0) + size.width;
                widest = Math.max(widest, x);
                rowHeight = Math.max(rowHeight, size.height);
            }
            height += rowHeight;
            return new Dimension(widest + insets.left + insets.right + getHgap() * 2,
                    height + insets.top + insets.bottom + getVgap() * 2);
        }
    }

    @Override
    public void layoutContainer(Container target) {
        super.layoutContainer(target);
        int needed = preferredLayoutSize(target).height;
        if (needed != target.getHeight() && needed != requested && target instanceof JComponent component) {
            requested = needed;
            SwingUtilities.invokeLater(component::revalidate);
        }
    }
}
