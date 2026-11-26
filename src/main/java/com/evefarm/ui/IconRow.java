package com.evefarm.ui;

import javax.swing.Icon;
import java.awt.Component;
import java.awt.Graphics;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

final class IconRow implements Icon {

    private static final int GAP = 4;

    private final List<Icon> icons;

    private IconRow(List<Icon> icons) {
        this.icons = icons;
    }

    static Icon of(Icon... icons) {
        List<Icon> shown = Stream.of(icons).filter(Objects::nonNull).toList();
        if (shown.isEmpty()) {
            return null;
        }
        return shown.size() == 1 ? shown.getFirst() : new IconRow(shown);
    }

    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
        int height = getIconHeight();
        int left = x;
        for (Icon icon : icons) {
            icon.paintIcon(component, graphics, left, y + (height - icon.getIconHeight()) / 2);
            left += icon.getIconWidth() + GAP;
        }
    }

    @Override
    public int getIconWidth() {
        return icons.stream().mapToInt(Icon::getIconWidth).sum() + GAP * (icons.size() - 1);
    }

    @Override
    public int getIconHeight() {
        return icons.stream().mapToInt(Icon::getIconHeight).max().orElse(0);
    }
}
