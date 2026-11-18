package com.evefarm.ui;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.Icon;
import java.awt.Color;

final class PhosphorIcons {

    static final int BAR_SIZE = 18;
    static final int MENU_SIZE = 16;

    enum Hue {
        NEUTRAL(0x475569, 0x94A3B8),
        BLUE(0x2563EB, 0x60A5FA),
        GREEN(0x16A34A, 0x4ADE80),
        AMBER(0xD97706, 0xFBBF24),
        RED(0xDC2626, 0xF87171),
        VIOLET(0x7C3AED, 0xA78BFA),
        ORANGE(0xEA580C, 0xFB923C),
        TEAL(0x0D9488, 0x2DD4BF),
        PINK(0xDB2777, 0xF472B6);

        private final Color light;
        private final Color dark;

        Hue(int light, int dark) {
            this.light = new Color(light);
            this.dark = new Color(dark);
        }

        Color color() {
            return FlatLaf.isLafDark() ? dark : light;
        }
    }

    static Icon bar(String name, Hue hue) {
        return icon(name, hue, BAR_SIZE);
    }

    static Icon menu(String name, Hue hue) {
        return icon(name, hue, MENU_SIZE);
    }

    static Icon icon(String name, Hue hue, int size) {
        FlatSVGIcon icon = new FlatSVGIcon("icons/phosphor/" + name + ".svg", size, size);
        icon.setColorFilter(new FlatSVGIcon.ColorFilter(color -> hue.color()));
        return icon;
    }

    private PhosphorIcons() {
    }
}
