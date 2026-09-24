package com.evefarm.ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuBar;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.AWTEvent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.geom.Line2D;

final class WindowChrome {

    private static final int BAR_HEIGHT = 32;
    private static final int BUTTON_WIDTH = 46;
    private static final int RESIZE_BAND = 6;
    private static final Color CLOSE_HOVER = new Color(0xE81123);
    private static final Color CLOSE_PRESSED = new Color(0xF1707A);

    private WindowChrome() {
    }

    static void install(JFrame frame, JMenuBar menuBar) {
        if (frame.isDisplayable()) {
            frame.dispose();
        }
        frame.setUndecorated(true);
        Color edge = UIManager.getColor("controlShadow");
        frame.getRootPane().setBorder(BorderFactory.createLineBorder(edge == null ? Color.GRAY : edge));

        Image appIcon = frame.getIconImage();
        if (appIcon != null) {
            JLabel icon = new JLabel(new ImageIcon(appIcon.getScaledInstance(16, 16, Image.SCALE_SMOOTH)));
            icon.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 6));
            menuBar.add(icon, 0);
            addDragSupport(frame, icon);
        }
        menuBar.add(Box.createHorizontalStrut(8));
        menuBar.add(new CaptionButton(CaptionButton.Kind.MINIMIZE,
                () -> frame.setExtendedState(frame.getExtendedState() | Frame.ICONIFIED)));
        menuBar.add(new CaptionButton(CaptionButton.Kind.CLOSE,
                () -> frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING))));
        addDragSupport(frame, menuBar);

        frame.setMaximizedBounds(usableScreenBounds(frame));
        frame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent e) {
                if (!isMaximized(frame)) {
                    frame.setMaximizedBounds(usableScreenBounds(frame));
                }
            }
        });
        new EdgeResizer(frame).install();
    }

    static boolean isMaximized(Frame frame) {
        return (frame.getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH;
    }

    static void toggleMaximize(JFrame frame) {
        if (isMaximized(frame)) {
            frame.setExtendedState(Frame.NORMAL);
        } else {
            frame.setMaximizedBounds(usableScreenBounds(frame));
            frame.setExtendedState(Frame.MAXIMIZED_BOTH);
        }
    }

    static Rectangle usableScreenBounds(Component component) {
        GraphicsConfiguration configuration = component.getGraphicsConfiguration();
        if (configuration == null) {
            return null;
        }
        Rectangle screen = configuration.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
        return new Rectangle(screen.x + insets.left, screen.y + insets.top,
                screen.width - insets.left - insets.right, screen.height - insets.top - insets.bottom);
    }

    private static void addDragSupport(JFrame frame, Component handle) {
        MouseAdapter drag = new MouseAdapter() {
            private Point pressedOnScreen;
            private Point windowAtPress;

            @Override
            public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e) || EdgeResizer.isResizing(frame)) {
                    return;
                }
                pressedOnScreen = e.getLocationOnScreen();
                windowAtPress = frame.getLocation();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (pressedOnScreen == null || EdgeResizer.isResizing(frame)) {
                    return;
                }
                Point now = e.getLocationOnScreen();
                if (isMaximized(frame)) {
                    double ratio = (double) e.getX() / Math.max(1, handle.getWidth());
                    frame.setExtendedState(Frame.NORMAL);
                    int width = frame.getWidth();
                    windowAtPress = new Point(now.x - (int) (width * ratio), now.y - e.getY());
                    pressedOnScreen = now;
                }
                frame.setLocation(windowAtPress.x + now.x - pressedOnScreen.x,
                        windowAtPress.y + now.y - pressedOnScreen.y);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                pressedOnScreen = null;
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
                    toggleMaximize(frame);
                }
            }
        };
        handle.addMouseListener(drag);
        handle.addMouseMotionListener(drag);
    }

    private static final class CaptionButton extends JButton {

        enum Kind { MINIMIZE, CLOSE }

        private final Kind kind;

        CaptionButton(Kind kind, Runnable action) {
            this.kind = kind;
            setFocusable(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setRolloverEnabled(true);
            setToolTipText(kind == Kind.CLOSE ? "Close" : "Minimize");
            addActionListener(e -> action.run());
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(BUTTON_WIDTH, BAR_HEIGHT);
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean hover = getModel().isRollover();
            boolean pressed = getModel().isPressed();
            Color glyph = UIManager.getColor("MenuBar.foreground");
            if (glyph == null) {
                glyph = Color.BLACK;
            }
            if (kind == Kind.CLOSE && (hover || pressed)) {
                g2.setColor(pressed ? CLOSE_PRESSED : CLOSE_HOVER);
                g2.fillRect(0, 0, getWidth(), getHeight());
                glyph = Color.WHITE;
            } else if (hover || pressed) {
                g2.setColor(new Color(0, 0, 0, pressed ? 40 : 22));
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
            g2.setColor(glyph);
            g2.setStroke(new BasicStroke(1f));
            double cx = getWidth() / 2.0;
            double cy = getHeight() / 2.0;
            if (kind == Kind.CLOSE) {
                g2.draw(new Line2D.Double(cx - 5, cy - 5, cx + 5, cy + 5));
                g2.draw(new Line2D.Double(cx + 5, cy - 5, cx - 5, cy + 5));
            } else {
                g2.draw(new Line2D.Double(cx - 5, cy + 0.5, cx + 5, cy + 0.5));
            }
            g2.dispose();
        }
    }

    private static final class EdgeResizer {

        private static final String RESIZING = "evefarm.resizing";

        private final JFrame frame;
        private int edge;
        private Rectangle boundsAtPress;
        private Point pressedOnScreen;

        EdgeResizer(JFrame frame) {
            this.frame = frame;
        }

        static boolean isResizing(JFrame frame) {
            return Boolean.TRUE.equals(frame.getRootPane().getClientProperty(RESIZING));
        }

        void install() {
            Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
                if (event instanceof MouseEvent mouse && belongsToFrame(mouse.getComponent())) {
                    handle(mouse);
                }
            }, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
        }

        private boolean belongsToFrame(Component component) {
            return component == frame || (component != null && SwingUtilities.getWindowAncestor(component) == frame);
        }

        private void handle(MouseEvent e) {
            if (isMaximized(frame) && boundsAtPress == null) {
                return;
            }
            Point inFrame = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), frame);
            switch (e.getID()) {
                case MouseEvent.MOUSE_MOVED -> frame.getRootPane().setCursor(cursorFor(edgeAt(inFrame)));
                case MouseEvent.MOUSE_EXITED -> {
                    if (boundsAtPress == null && e.getComponent() == frame.getRootPane()) {
                        frame.getRootPane().setCursor(null);
                    }
                }
                case MouseEvent.MOUSE_PRESSED -> {
                    int at = edgeAt(inFrame);
                    if (at != 0 && SwingUtilities.isLeftMouseButton(e)) {
                        edge = at;
                        boundsAtPress = frame.getBounds();
                        pressedOnScreen = e.getLocationOnScreen();
                        frame.getRootPane().putClientProperty(RESIZING, true);
                    }
                }
                case MouseEvent.MOUSE_DRAGGED -> {
                    if (boundsAtPress != null) {
                        resizeTo(e.getLocationOnScreen());
                    }
                }
                case MouseEvent.MOUSE_RELEASED -> {
                    boundsAtPress = null;
                    frame.getRootPane().putClientProperty(RESIZING, false);
                }
                default -> {
                }
            }
        }

        private int edgeAt(Point p) {
            int result = 0;
            if (p.x < RESIZE_BAND) {
                result |= 1;
            } else if (p.x >= frame.getWidth() - RESIZE_BAND) {
                result |= 2;
            }
            if (p.y < RESIZE_BAND) {
                result |= 4;
            } else if (p.y >= frame.getHeight() - RESIZE_BAND) {
                result |= 8;
            }
            return result;
        }

        private static Cursor cursorFor(int edge) {
            return switch (edge) {
                case 1 -> Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR);
                case 2 -> Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR);
                case 4 -> Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR);
                case 8 -> Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR);
                case 5 -> Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR);
                case 6 -> Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR);
                case 9 -> Cursor.getPredefinedCursor(Cursor.SW_RESIZE_CURSOR);
                case 10 -> Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR);
                default -> null;
            };
        }

        private void resizeTo(Point mouse) {
            int dx = mouse.x - pressedOnScreen.x;
            int dy = mouse.y - pressedOnScreen.y;
            Dimension min = frame.getMinimumSize();
            Rectangle b = new Rectangle(boundsAtPress);
            if ((edge & 1) != 0) {
                int width = Math.max(min.width, boundsAtPress.width - dx);
                b.x = boundsAtPress.x + boundsAtPress.width - width;
                b.width = width;
            }
            if ((edge & 2) != 0) {
                b.width = Math.max(min.width, boundsAtPress.width + dx);
            }
            if ((edge & 4) != 0) {
                int height = Math.max(min.height, boundsAtPress.height - dy);
                b.y = boundsAtPress.y + boundsAtPress.height - height;
                b.height = height;
            }
            if ((edge & 8) != 0) {
                b.height = Math.max(min.height, boundsAtPress.height + dy);
            }
            frame.setBounds(b);
            frame.validate();
        }
    }
}
