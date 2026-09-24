package com.evefarm.ui;

import javax.swing.Icon;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Consumer;

public final class Icons {

    private static final int SIZE = 16;
    private static final float STROKE = 1.5f;

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

    public static final Icon ADD = icon(Tone.SUCCESS, g -> {
        g.draw(line(8, 3, 8, 13));
        g.draw(line(3, 8, 13, 8));
    });

    public static final Icon REMOVE = icon(Tone.DANGER, g -> {
        g.draw(line(2.5, 4.5, 13.5, 4.5));
        g.draw(path(6, 4.5, 6, 2.75, 10, 2.75, 10, 4.5));
        g.draw(path(3.75, 4.5, 4.5, 13.25, 11.5, 13.25, 12.25, 4.5));
        g.draw(line(6.75, 7, 6.75, 11));
        g.draw(line(9.25, 7, 9.25, 11));
    });

    public static final Icon CLEAR = icon(Tone.NEUTRAL, g -> {
        g.draw(closed(2.5, 10, 8.5, 4, 12.5, 8, 6.5, 14));
        g.draw(line(5.5, 7, 9.5, 11));
        g.draw(line(6.5, 14, 13.5, 14));
    });

    public static final Icon SAVE = icon(Tone.NEUTRAL, g -> {
        g.draw(closed(2.5, 2.5, 11, 2.5, 13.5, 5, 13.5, 13.5, 2.5, 13.5));
        g.draw(path(5, 2.5, 5, 5.5, 10, 5.5, 10, 2.5));
        g.draw(path(4.75, 13.5, 4.75, 9, 11.25, 9, 11.25, 13.5));
    });

    public static final Icon LOAD = icon(Tone.NEUTRAL, g -> {
        g.draw(path(2.5, 12.5, 2.5, 3.5, 6, 3.5, 7.5, 5, 12, 5, 12, 7.5));
        g.draw(closed(2.5, 12.5, 4.5, 7.5, 14, 7.5, 12, 12.5));
    });

    public static final Icon REFRESH = icon(Tone.ACCENT, g -> {
        g.draw(new Arc2D.Double(2.5, 2.5, 11, 11, 60, 300, Arc2D.OPEN));
        g.fill(closed(9.9, 0.9, 13.3, 3.3, 9.9, 5.7));
    });

    public static final Icon CANCEL = icon(Tone.DANGER, g -> {
        g.draw(line(4, 4, 12, 12));
        g.draw(line(12, 4, 4, 12));
    });

    public static final Icon PLAY = icon(Tone.SUCCESS, g -> {
        Shape triangle = closed(5, 3, 13, 8, 5, 13);
        g.fill(triangle);
        g.draw(triangle);
    });

    public static final Icon EYE = icon(Tone.NEUTRAL, g -> {
        Path2D eye = new Path2D.Double();
        eye.moveTo(1.5, 8);
        eye.quadTo(8, 1.5, 14.5, 8);
        eye.quadTo(8, 14.5, 1.5, 8);
        eye.closePath();
        g.draw(eye);
        g.draw(new Ellipse2D.Double(5.75, 5.75, 4.5, 4.5));
    });

    public static final Icon GEAR = icon(Tone.NEUTRAL, g -> {
        Path2D gear = new Path2D.Double();
        for (int tooth = 0; tooth < 8; tooth++) {
            double base = Math.toRadians(tooth * 45);
            point(gear, tooth == 0, base - Math.toRadians(16), 4.9);
            point(gear, false, base - Math.toRadians(8), 6.5);
            point(gear, false, base + Math.toRadians(8), 6.5);
            point(gear, false, base + Math.toRadians(16), 4.9);
        }
        gear.closePath();
        g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(gear);
        g.draw(new Ellipse2D.Double(5.8, 5.8, 4.4, 4.4));
    });

    public static final Icon DOOR = icon(Tone.NEUTRAL, g -> {
        g.draw(path(6.5, 2.5, 3, 2.5, 3, 13.5, 6.5, 13.5));
        g.draw(line(6.5, 8, 13.5, 8));
        g.draw(path(11, 5.5, 13.5, 8, 11, 10.5));
    });

    public static final Icon PERSON = icon(Tone.NEUTRAL, g -> {
        g.draw(new Ellipse2D.Double(5.5, 2.25, 5, 5));
        g.draw(new Arc2D.Double(2.5, 9.25, 11, 9, 0, 180, Arc2D.OPEN));
    });

    public static final Icon TARGET = icon(Tone.NEUTRAL, g -> {
        g.draw(new Ellipse2D.Double(2, 2, 12, 12));
        g.draw(new Ellipse2D.Double(5, 5, 6, 6));
        g.fill(new Ellipse2D.Double(7, 7, 2, 2));
    });

    public static final Icon ARCHIVE = icon(Tone.NEUTRAL, g -> {
        g.draw(new RoundRectangle2D.Double(2, 2.5, 12, 3.5, 2, 2));
        g.draw(path(3, 6, 3, 13.5, 13, 13.5, 13, 6));
        g.draw(line(6.5, 9, 9.5, 9));
    });

    public static final Icon COIN = icon(Tone.NEUTRAL, g -> {
        g.draw(closed(8, 1.8, 13.8, 5, 13.8, 11, 8, 14.2, 2.2, 11, 2.2, 5));
        g.draw(path(2.2, 5, 8, 8.2, 13.8, 5));
        g.draw(line(8, 8.2, 8, 14.2));
    });

    public static final Icon CHART = icon(Tone.NEUTRAL, g -> {
        g.draw(path(2.5, 2.5, 2.5, 13.5, 13.5, 13.5));
        g.draw(path(4.75, 10.5, 7.25, 7.5, 9.5, 9.25, 13, 4.5));
    });

    public static final Icon NOTEBOOK = icon(Tone.NEUTRAL, g -> {
        g.draw(new RoundRectangle2D.Double(3, 1.75, 10, 12.5, 2.5, 2.5));
        g.draw(line(5.75, 1.75, 5.75, 14.25));
        g.draw(line(8, 5.5, 10.75, 5.5));
        g.draw(line(8, 8.25, 10.75, 8.25));
    });

    public static final Icon CLIPBOARD = icon(Tone.NEUTRAL, g -> {
        g.draw(path(5.5, 3.25, 3, 3.25, 3, 14.5, 13, 14.5, 13, 3.25, 10.5, 3.25));
        g.draw(new RoundRectangle2D.Double(5.5, 1.75, 5, 3, 1.5, 1.5));
        g.draw(line(5.5, 8, 10.5, 8));
        g.draw(line(5.5, 10.75, 9, 10.75));
    });

    public static final Icon SWAP = icon(Tone.NEUTRAL, g -> {
        g.draw(line(3, 5.5, 13, 5.5));
        g.draw(path(10.5, 3, 13, 5.5, 10.5, 8));
        g.draw(line(13, 10.5, 3, 10.5));
        g.draw(path(5.5, 8, 3, 10.5, 5.5, 13));
    });

    public static final Icon DOCUMENT = icon(Tone.NEUTRAL, g -> {
        g.draw(closed(3.5, 1.75, 9.5, 1.75, 12.75, 5, 12.75, 14.25, 3.5, 14.25));
        g.draw(path(9.5, 1.75, 9.5, 5, 12.75, 5));
        g.draw(line(5.75, 8.5, 10.5, 8.5));
        g.draw(line(5.75, 11, 10.5, 11));
    });

    public static final Icon FACTORY = icon(Tone.NEUTRAL, g -> {
        g.draw(closed(2, 13.75, 2, 7.5, 5.5, 9.75, 5.5, 7.5, 9, 9.75, 9, 2.5, 12.75, 2.5, 12.75, 13.75));
        g.draw(line(4.25, 11.75, 5.25, 11.75));
        g.draw(line(7.25, 11.75, 8.25, 11.75));
    });

    public static final Icon VALUE = icon(Tone.NEUTRAL, g -> {
        g.draw(new RoundRectangle2D.Double(2.25, 8.5, 3, 5.25, 1.5, 1.5));
        g.draw(new RoundRectangle2D.Double(6.5, 2.75, 3, 11, 1.5, 1.5));
        g.draw(new RoundRectangle2D.Double(10.75, 6, 3, 7.75, 1.5, 1.5));
    });

    public static final Icon LP = icon(Tone.NEUTRAL, g -> g.draw(star(6.5, 2.9)));

    public static final Icon MAIN = icon(Tone.FAVORITE, g -> {
        Path2D star = star(4.8, 2.1);
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.fill(star);
        g.draw(star);
    });

    public static final Icon BLANK = icon(Tone.NEUTRAL, g -> {
    });

    public static final Icon CROSSHAIR = icon(Tone.NEUTRAL, g -> {
        g.draw(new Ellipse2D.Double(2.5, 2.5, 11, 11));
        g.draw(line(8, 1, 8, 4.75));
        g.draw(line(8, 11.25, 8, 15));
        g.draw(line(1, 8, 4.75, 8));
        g.draw(line(11.25, 8, 15, 8));
    });

    public static final Icon MEDAL = icon(Tone.NEUTRAL, g -> {
        g.draw(new Ellipse2D.Double(4, 1.5, 8, 8));
        g.draw(path(5.8, 8.6, 4.5, 14.5, 8, 12.8, 11.5, 14.5, 10.2, 8.6));
    });

    public static Icon disabled(Icon icon) {
        if (icon instanceof LineIcon line) {
            return new LineIcon(line.tone, line.painter, true);
        }
        return icon;
    }

    private static Icon icon(Tone tone, Consumer<Graphics2D> painter) {
        return new LineIcon(tone, painter, false);
    }

    private static final class LineIcon implements Icon {

        private final Tone tone;
        private final Consumer<Graphics2D> painter;
        private final boolean alwaysDisabled;

        private LineIcon(Tone tone, Consumer<Graphics2D> painter, boolean alwaysDisabled) {
            this.tone = tone;
            this.painter = painter;
            this.alwaysDisabled = alwaysDisabled;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.translate(x, y);
            g2.setColor(alwaysDisabled || (c != null && !c.isEnabled()) ? disabledColor() : tone.color());
            g2.setStroke(new BasicStroke(STROKE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            painter.accept(g2);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return SIZE;
        }

        @Override
        public int getIconHeight() {
            return SIZE;
        }
    }

    private static Color disabledColor() {
        Color themed = UIManager.getColor("Label.disabledForeground");
        return themed != null ? themed : Color.GRAY;
    }

    private static Shape line(double x1, double y1, double x2, double y2) {
        return new Line2D.Double(x1, y1, x2, y2);
    }

    private static Path2D path(double... points) {
        Path2D path = new Path2D.Double();
        path.moveTo(points[0], points[1]);
        for (int i = 2; i < points.length; i += 2) {
            path.lineTo(points[i], points[i + 1]);
        }
        return path;
    }

    private static Path2D closed(double... points) {
        Path2D path = path(points);
        path.closePath();
        return path;
    }

    private static Path2D star(double outerRadius, double innerRadius) {
        Path2D star = new Path2D.Double();
        for (int i = 0; i < 10; i++) {
            double angle = Math.toRadians(-90 + i * 36);
            double radius = i % 2 == 0 ? outerRadius : innerRadius;
            double x = 8 + Math.cos(angle) * radius;
            double y = 8.6 + Math.sin(angle) * radius;
            if (i == 0) {
                star.moveTo(x, y);
            } else {
                star.lineTo(x, y);
            }
        }
        star.closePath();
        return star;
    }

    private static void point(Path2D path, boolean first, double angle, double radius) {
        double x = 8 + Math.cos(angle) * radius;
        double y = 8 + Math.sin(angle) * radius;
        if (first) {
            path.moveTo(x, y);
        } else {
            path.lineTo(x, y);
        }
    }

    private Icons() {
    }
}
