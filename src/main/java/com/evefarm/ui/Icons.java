package com.evefarm.ui;

import javax.swing.Icon;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;

public final class Icons {

    private static final int SIZE = 16;
    private static final int DROPDOWN_SIZE = 10;

    private enum Tone {
        NEUTRAL("Actions.Grey", new Color(0x6E6E6E)),
        ACCENT("Component.accentColor", new Color(0x2D6FD6)),
        SUCCESS("Actions.Green", new Color(0x59A869)),
        DANGER("Actions.Red", new Color(0xDB5860)),
        FAVORITE("Actions.Yellow", new Color(0xE0A800));

        private final String key;
        private final Color fallback;

        Tone(String key, Color fallback) {
            this.key = key;
            this.fallback = fallback;
        }

        Color color() {
            Color themed = UIManager.getColor(key);
            return themed != null ? themed : fallback;
        }
    }

    static Color accent() {
        return Tone.ACCENT.color();
    }

    public static final Icon ADD = icon("plus", Tone.SUCCESS);
    public static final Icon REMOVE = icon("trash-can", Tone.DANGER);
    public static final Icon CLEAR = icon("eraser", Tone.NEUTRAL);
    public static final Icon SAVE = icon("floppy-disk", Tone.NEUTRAL);
    public static final Icon LOAD = icon("folder-open", Tone.NEUTRAL);
    public static final Icon REFRESH = icon("arrows-rotate", Tone.ACCENT);
    public static final Icon CANCEL = icon("xmark", Tone.DANGER);
    public static final Icon PLAY = icon("play", Tone.SUCCESS);
    public static final Icon EYE = icon("eye", Tone.NEUTRAL);
    public static final Icon GEAR = icon("gear", Tone.NEUTRAL);
    public static final Icon TARGET = icon("bullseye", Tone.NEUTRAL);
    public static final Icon ARCHIVE = icon("box-archive", Tone.NEUTRAL);
    public static final Icon DROPDOWN = FontAwesome.icon("chevron-down", Tone.NEUTRAL::color, DROPDOWN_SIZE,
            DROPDOWN_SIZE, SIZE);
    public static final Icon CHART = icon("chart-line", Tone.NEUTRAL);
    public static final Icon NOTEBOOK = icon("book", Tone.NEUTRAL);
    public static final Icon CLIPBOARD = icon("clipboard", Tone.NEUTRAL);
    public static final Icon SWAP = icon("right-left", Tone.NEUTRAL);
    public static final Icon DOCUMENT = icon("file-lines", Tone.NEUTRAL);
    public static final Icon PACKAGE = icon("box", Tone.NEUTRAL);
    public static final Icon MAIN = icon("star", Tone.FAVORITE);
    public static final Icon BLANK = new Blank();
    public static final Icon KILLBOARD = icon("skull-crossbones", Tone.DANGER);
    public static final Icon INFO = icon("circle-info", Tone.ACCENT);
    public static final Icon CALCULATOR = icon("calculator", Tone.NEUTRAL);
    public static final Icon MOVE_UP = icon("arrow-up", Tone.NEUTRAL);
    public static final Icon MOVE_DOWN = icon("arrow-down", Tone.NEUTRAL);
    public static final Icon PREVIOUS = icon("chevron-left", Tone.NEUTRAL);
    public static final Icon NEXT = icon("chevron-right", Tone.NEUTRAL);
    public static final Icon SEARCH = icon("magnifying-glass", Tone.NEUTRAL);
    public static final Icon FILTER = icon("filter", Tone.NEUTRAL);

    private Icons() {
    }

    public static Icon disabled(Icon icon) {
        return FontAwesome.disabled(icon);
    }

    private static Icon icon(String name, Tone tone) {
        return FontAwesome.icon(name, tone::color, SIZE);
    }

    private static final class Blank implements Icon {

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
        }

        @Override
        public int getIconWidth() {
            return SIZE;
        }

        @Override
        public int getIconHeight() {
            return SIZE;
        }

        @Override
        public String toString() {
            return "";
        }
    }
}
