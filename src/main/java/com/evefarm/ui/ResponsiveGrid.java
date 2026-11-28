package com.evefarm.ui;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.util.Arrays;

final class ResponsiveGrid implements LayoutManager {

    private final int gap;
    private int requested = -1;

    ResponsiveGrid(int gap) {
        this.gap = gap;
    }

    static int columns(int available, int cellWidth, int count, int gap) {
        if (count == 0) {
            return 1;
        }
        int fit = (available + gap) / Math.max(1, cellWidth + gap);
        return Math.max(1, Math.min(count, fit));
    }

    @Override
    public void addLayoutComponent(String name, Component component) {
    }

    @Override
    public void removeLayoutComponent(Component component) {
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        return size(parent, parent.getWidth() > 0 ? parent.getWidth() : Integer.MAX_VALUE);
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        Component[] cells = visible(parent);
        Insets insets = parent.getInsets();
        return new Dimension(cellWidth(cells) + insets.left + insets.right,
                Arrays.stream(cells).mapToInt(cell -> cell.getPreferredSize().height).max().orElse(0)
                        + insets.top + insets.bottom);
    }

    @Override
    public void layoutContainer(Container parent) {
        Component[] cells = visible(parent);
        Insets insets = parent.getInsets();
        int available = parent.getWidth() - insets.left - insets.right;
        int columns = columns(available, cellWidth(cells), cells.length, gap);
        int width = (available - gap * (columns - 1)) / columns;
        int y = insets.top;
        for (int start = 0; start < cells.length; start += columns) {
            int rowHeight = 0;
            for (int i = start; i < Math.min(cells.length, start + columns); i++) {
                rowHeight = Math.max(rowHeight, cells[i].getPreferredSize().height);
            }
            for (int i = start; i < Math.min(cells.length, start + columns); i++) {
                cells[i].setBounds(insets.left + (i - start) * (width + gap), y, width,
                        cells[i].getPreferredSize().height);
            }
            y += rowHeight + gap;
        }
        int needed = size(parent, parent.getWidth()).height;
        if (needed != parent.getHeight() && needed != requested && parent instanceof JComponent component) {
            requested = needed;
            SwingUtilities.invokeLater(component::revalidate);
        }
    }

    private Dimension size(Container parent, int width) {
        Component[] cells = visible(parent);
        Insets insets = parent.getInsets();
        int cellWidth = cellWidth(cells);
        int columns = width == Integer.MAX_VALUE ? Math.max(1, cells.length)
                : columns(width - insets.left - insets.right, cellWidth, cells.length, gap);
        int height = 0;
        for (int start = 0; start < cells.length; start += columns) {
            int rowHeight = 0;
            for (int i = start; i < Math.min(cells.length, start + columns); i++) {
                rowHeight = Math.max(rowHeight, cells[i].getPreferredSize().height);
            }
            height += rowHeight + (start > 0 ? gap : 0);
        }
        int preferredWidth = columns * cellWidth + gap * (columns - 1);
        return new Dimension(preferredWidth + insets.left + insets.right, height + insets.top + insets.bottom);
    }

    private static int cellWidth(Component[] cells) {
        return Arrays.stream(cells).mapToInt(cell -> cell.getPreferredSize().width).max().orElse(0);
    }

    private static Component[] visible(Container parent) {
        return Arrays.stream(parent.getComponents()).filter(Component::isVisible).toArray(Component[]::new);
    }
}
