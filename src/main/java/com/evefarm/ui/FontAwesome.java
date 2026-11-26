package com.evefarm.ui;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.Icon;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.util.function.Supplier;

final class FontAwesome {

    static final int BAR_SIZE = 18;
    static final int MENU_SIZE = 16;

    private static final Color LIGHT = new Color(0x475569);
    private static final Color DARK = new Color(0x94A3B8);

    private FontAwesome() {
    }

    static Color neutral() {
        return FlatLaf.isLafDark() ? DARK : LIGHT;
    }

    static Icon bar(String name) {
        return icon(name, FontAwesome::neutral, BAR_SIZE);
    }

    static Icon menu(String name) {
        return icon(name, FontAwesome::neutral, MENU_SIZE);
    }

    static Icon icon(String name, Supplier<Color> color, int size) {
        return icon(name, color, size, size, size);
    }

    static Icon icon(String name, Supplier<Color> color, int glyphSize, int width, int height) {
        return new Glyph(svg(name, glyphSize, color), svg(name, glyphSize, UiColors::muted), width, height, false);
    }

    static Icon disabled(Icon icon) {
        return icon instanceof Glyph glyph
                ? new Glyph(glyph.normal(), glyph.muted(), glyph.width(), glyph.height(), true) : icon;
    }

    private static FlatSVGIcon svg(String name, int size, Supplier<Color> color) {
        FlatSVGIcon icon = new FlatSVGIcon("icons/fontawesome/" + name + ".svg", size, size);
        icon.setColorFilter(new FlatSVGIcon.ColorFilter(original -> color.get()));
        return icon;
    }

    private record Glyph(Icon normal, Icon muted, int width, int height, boolean alwaysDisabled) implements Icon {

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Icon shown = alwaysDisabled || component != null && !component.isEnabled() ? muted : normal;
            shown.paintIcon(component, graphics, x + (width - shown.getIconWidth()) / 2,
                    y + (height - shown.getIconHeight()) / 2);
        }

        @Override
        public int getIconWidth() {
            return width;
        }

        @Override
        public int getIconHeight() {
            return height;
        }

        @Override
        public String toString() {
            return "";
        }
    }
}
