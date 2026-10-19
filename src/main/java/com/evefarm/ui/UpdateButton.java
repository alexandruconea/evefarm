package com.evefarm.ui;

import com.formdev.flatlaf.FlatLaf;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.Timer;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.time.Instant;

final class UpdateButton extends JButton {

    private static final int ICON_SIZE = 20;
    private static final Dimension SIZE = new Dimension(34, 30);

    private final DataUpdater updater;
    private final Timer spin = new Timer(40, e -> turn());
    private final Timer countdown = new Timer(1000, e -> refresh());
    private int angle;

    UpdateButton(DataUpdater updater) {
        this.updater = updater;
        Icon icon = new StateIcon();
        setIcon(icon);
        setDisabledIcon(icon);
        setFocusable(false);
        setRolloverEnabled(true);
        putClientProperty("JButton.buttonType", "toolBarButton");
        if (!(UIManager.getLookAndFeel() instanceof FlatLaf)) {
            setContentAreaFilled(false);
            setBorderPainted(false);
        }
        setPreferredSize(SIZE);
        setMinimumSize(SIZE);
        setMaximumSize(SIZE);
        addActionListener(e -> updater.update(updater.readyKeys(Instant.now()), false));
        updater.addListener(this::refresh);
        countdown.start();
        refresh();
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (!isContentAreaFilled() && isEnabled() && getModel().isRollover()) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(0, 0, 0, getModel().isPressed() ? 34 : 18));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
            g2.dispose();
        }
        super.paintComponent(g);
    }

    private void turn() {
        angle = (angle + 12) % 360;
        repaint();
    }

    private void refresh() {
        Instant now = Instant.now();
        boolean busy = updater.busy();
        if (busy != spin.isRunning()) {
            if (busy) {
                spin.start();
            } else {
                spin.stop();
                angle = 0;
            }
            repaint();
        }
        setEnabled(!busy && !updater.readyKeys(now).isEmpty());
        String tip = updater.status(now);
        if (!tip.equals(getToolTipText())) {
            setToolTipText(tip);
            Point mouse = getMousePosition();
            if (mouse != null) {
                ToolTipManager.sharedInstance().mouseMoved(new MouseEvent(this, MouseEvent.MOUSE_MOVED,
                        System.currentTimeMillis(), 0, mouse.x, mouse.y, 0, false));
            }
        }
    }

    private final class StateIcon implements Icon {

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.translate(x, y);
            if (updater.busy()) {
                Color accent = Icons.accent();
                g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 60));
                g2.draw(new Ellipse2D.Double(2.5, 2.5, 15, 15));
                g2.setColor(accent);
                g2.draw(new Arc2D.Double(2.5, 2.5, 15, 15, 90 - angle, 110, Arc2D.OPEN));
            } else {
                g2.scale(ICON_SIZE / 16.0, ICON_SIZE / 16.0);
                Icons.REFRESH.paintIcon(c, g2, 0, 0);
            }
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return ICON_SIZE;
        }

        @Override
        public int getIconHeight() {
            return ICON_SIZE;
        }
    }
}
