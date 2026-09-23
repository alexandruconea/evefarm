package com.evefarm.ui;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;

public final class Icons {

    private static final int SIZE = 16;

    public static final Icon ADD = shape(g -> {
        fillBadge(g, new Color(67, 160, 71));
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(2f));
        g.drawLine(8, 4, 8, 12);
        g.drawLine(4, 8, 12, 8);
    });

    public static final Icon REMOVE = shape(g -> {
        fillBadge(g, new Color(211, 47, 47));
        g.setColor(Color.WHITE);
        g.fillRect(4, 6, 8, 7);
        g.fillRect(3, 4, 10, 2);
        g.fillRect(6, 2, 4, 2);
    });

    public static final Icon CLEAR = shape(g -> {
        fillBadge(g, new Color(117, 117, 117));
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(4, 3, 10, 9);
        g.fillPolygon(new int[]{9, 13, 12, 8}, new int[]{8, 9, 13, 12}, 4);
    });

    public static final Icon SAVE = shape(g -> {
        fillBadge(g, new Color(30, 136, 229));
        g.setColor(Color.WHITE);
        g.fillRoundRect(3, 3, 10, 10, 2, 2);
        g.setColor(new Color(30, 136, 229));
        g.fillRect(9, 3, 3, 3);
        g.fillRect(5, 8, 6, 4);
        g.setColor(Color.WHITE);
        g.fillRect(6, 9, 4, 2);
    });

    public static final Icon LOAD = shape(g -> {
        fillBadge(g, new Color(255, 179, 0));
        g.setColor(Color.WHITE);
        g.fillRect(3, 6, 10, 7);
        g.fillRect(3, 4, 5, 2);
    });

    public static final Icon REFRESH = shape(g -> {
        fillBadge(g, new Color(30, 136, 229));
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(2f));
        g.draw(new Arc2D.Float(3, 3, 10, 10, 30, 270, Arc2D.OPEN));
        g.fillPolygon(new int[]{11, 15, 12}, new int[]{2, 4, 6}, 3);
    });

    public static final Icon CANCEL = shape(g -> {
        fillBadge(g, new Color(211, 47, 47));
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(4, 4, 12, 12);
        g.drawLine(12, 4, 4, 12);
    });

    public static final Icon PLAY = shape(g -> {
        fillBadge(g, new Color(67, 160, 71));
        g.setColor(Color.WHITE);
        g.fillPolygon(new int[]{5, 5, 12}, new int[]{3, 13, 8}, 3);
    });

    public static final Icon EYE = shape(g -> {
        fillBadge(g, new Color(3, 155, 229));
        g.setColor(Color.WHITE);
        g.fillOval(2, 5, 12, 6);
        g.setColor(new Color(3, 155, 229));
        g.fillOval(6, 6, 4, 4);
    });

    public static final Icon GEAR = shape(g -> {
        fillBadge(g, new Color(97, 97, 97));
        g.setColor(Color.WHITE);
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2 * i / 8;
            int cx = 8 + (int) Math.round(Math.cos(angle) * 6);
            int cy = 8 + (int) Math.round(Math.sin(angle) * 6);
            g.fillRect(cx - 1, cy - 1, 2, 2);
        }
        g.fillOval(4, 4, 8, 8);
        g.setColor(new Color(97, 97, 97));
        g.fillOval(6, 6, 4, 4);
    });

    public static final Icon DOOR = shape(g -> {
        fillBadge(g, new Color(121, 85, 72));
        g.setColor(Color.WHITE);
        g.fillRect(4, 2, 8, 12);
        g.setColor(new Color(121, 85, 72));
        g.fillOval(9, 7, 2, 2);
    });

    public static final Icon PERSON = shape(g -> {
        fillBadge(g, new Color(69, 90, 100));
        g.setColor(Color.WHITE);
        g.fillOval(6, 3, 4, 4);
        g.fillArc(3, 8, 10, 9, 0, 180);
    });

    public static final Icon TARGET = shape(g -> {
        fillBadge(g, new Color(211, 47, 47));
        g.setColor(Color.WHITE);
        g.fillOval(2, 2, 12, 12);
        g.setColor(new Color(211, 47, 47));
        g.fillOval(4, 4, 8, 8);
        g.setColor(Color.WHITE);
        g.fillOval(6, 6, 4, 4);
    });

    public static final Icon ARCHIVE = shape(g -> {
        fillBadge(g, new Color(94, 53, 177));
        g.setColor(Color.WHITE);
        g.fillRect(3, 4, 10, 3);
        g.fillRect(4, 7, 8, 6);
        g.setColor(new Color(94, 53, 177));
        g.fillRect(6, 9, 4, 1);
    });

    public static final Icon COIN = shape(g -> {
        g.setColor(new Color(251, 192, 45));
        g.fillOval(1, 1, 14, 14);
        g.setColor(new Color(245, 127, 23));
        g.setStroke(new BasicStroke(1.2f));
        g.drawOval(1, 1, 14, 14);
        g.setFont(g.getFont().deriveFont(Font.BOLD, 10f));
        g.drawString("$", 5, 12);
    });

    public static final Icon CHART = shape(g -> {
        g.setColor(new Color(67, 160, 71));
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawPolyline(new int[]{2, 6, 9, 14}, new int[]{12, 8, 10, 3}, 4);
        g.fillOval(0, 10, 4, 4);
        g.fillOval(4, 6, 4, 4);
        g.fillOval(7, 8, 4, 4);
        g.fillOval(12, 1, 4, 4);
    });

    public static final Icon NOTEBOOK = shape(g -> {
        g.setColor(Color.WHITE);
        g.fillRect(3, 1, 12, 14);
        g.setColor(new Color(211, 47, 47));
        g.fillRect(1, 1, 3, 14);
        g.setColor(new Color(189, 189, 189));
        g.drawLine(6, 4, 13, 4);
        g.drawLine(6, 7, 13, 7);
        g.drawLine(6, 10, 13, 10);
    });

    public static final Icon CLIPBOARD = shape(g -> {
        g.setColor(new Color(120, 144, 156));
        g.fillRoundRect(2, 1, 12, 14, 2, 2);
        g.setColor(Color.WHITE);
        g.fillRect(3, 3, 10, 11);
        g.setColor(new Color(120, 144, 156));
        g.fillRect(6, 0, 4, 2);
        g.drawLine(5, 6, 11, 6);
        g.drawLine(5, 9, 11, 9);
        g.drawLine(5, 12, 11, 12);
    });

    public static final Icon SWAP = shape(g -> {
        g.setColor(new Color(0, 172, 193));
        g.setStroke(new BasicStroke(2f));
        g.drawLine(2, 5, 12, 5);
        g.fillPolygon(new int[]{12, 15, 12}, new int[]{2, 5, 8}, 3);
        g.drawLine(4, 11, 14, 11);
        g.fillPolygon(new int[]{4, 1, 4}, new int[]{8, 11, 14}, 3);
    });

    public static final Icon DOCUMENT = shape(g -> {
        g.setColor(new Color(96, 125, 139));
        g.fill(new Polygon(new int[]{3, 10, 13, 13, 3}, new int[]{1, 1, 4, 15, 15}, 5));
        g.setColor(Color.WHITE);
        g.drawLine(6, 6, 11, 6);
        g.drawLine(6, 9, 11, 9);
        g.drawLine(6, 12, 11, 12);
    });

    public static final Icon FACTORY = shape(g -> {
        g.setColor(new Color(109, 76, 65));
        g.fillRect(1, 8, 14, 7);
        g.fillRect(3, 3, 3, 6);
        g.fillRect(7, 5, 3, 4);
        g.setColor(new Color(189, 189, 189));
        g.fillOval(2, 1, 3, 3);
    });

    public static final Icon VALUE = shape(g -> {
        g.setColor(new Color(67, 160, 71));
        g.fillRoundRect(1, 10, 4, 5, 1, 1);
        g.fillRoundRect(6, 6, 4, 9, 1, 1);
        g.fillRoundRect(11, 1, 4, 14, 1, 1);
    });

    public static final Icon LP = shape(g -> {
        g.setColor(new Color(123, 31, 162));
        g.fillPolygon(new int[]{8, 10, 15, 11, 12, 8, 4, 5, 1, 6},
                new int[]{0, 5, 6, 9, 14, 11, 14, 9, 6, 5}, 10);
    });

    public static final Icon CROSSHAIR = shape(g -> {
        g.setColor(new Color(211, 47, 47));
        g.setStroke(new BasicStroke(1.5f));
        g.drawOval(2, 2, 12, 12);
        g.drawLine(8, 0, 8, 4);
        g.drawLine(8, 12, 8, 16);
        g.drawLine(0, 8, 4, 8);
        g.drawLine(12, 8, 16, 8);
        g.fillOval(6, 6, 4, 4);
    });

    public static final Icon MEDAL = shape(g -> {
        g.setColor(new Color(230, 81, 0));
        g.fillPolygon(new int[]{4, 8, 12}, new int[]{1, 7, 1}, 3);
        g.setColor(new Color(255, 202, 40));
        g.fillOval(3, 6, 10, 10);
        g.setColor(new Color(230, 81, 0));
        g.fillOval(6, 9, 4, 4);
    });

    public static Icon disabled(Icon icon) {
        int width = icon.getIconWidth();
        int height = icon.getIconHeight();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        icon.paintIcon(null, g, 0, 0);
        g.dispose();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = image.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xFF;
                if (alpha == 0) {
                    continue;
                }
                int r = (argb >> 16) & 0xFF;
                int gCh = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                int gray = (int) (0.3 * r + 0.59 * gCh + 0.11 * b);
                int fadedAlpha = alpha / 2;
                image.setRGB(x, y, (fadedAlpha << 24) | (gray << 16) | (gray << 8) | gray);
            }
        }
        return new ImageIcon(image);
    }

    private static void fillBadge(Graphics2D g, Color color) {
        g.setColor(color);
        g.fill(new RoundRectangle2D.Float(0, 0, SIZE, SIZE, 5, 5));
    }

    private static Icon shape(Consumer<Graphics2D> painter) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.translate(x, y);
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
        };
    }

    private Icons() {
    }
}
