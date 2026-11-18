package com.evefarm.ui;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

final class MainTabs {

    record Tab(String label, Icon icon, TabPanel panel) {
    }

    private static final String CAPTION = "JComponent.titleBarCaption";
    private static final int TAB_HEIGHT = 32;
    private static final int UNDERLINE_HEIGHT = 3;
    private static final int SIDE_PADDING = 10;
    private static final int ICON_TEXT_GAP = 6;
    private static final int ARROW_GAP = 4;

    private final JPanel strip = new JPanel(new StripLayout()) {
        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }
    };
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final List<TabButton> buttons = new ArrayList<>();
    private final Consumer<TabPanel> onSelected;
    private final BiConsumer<TabPanel, MouseEvent> onContextMenu;
    private TabPanel selected;
    private SubTabs pagesMenuClosedFor;
    private long pagesMenuClosedAt;

    MainTabs(Consumer<TabPanel> onSelected, BiConsumer<TabPanel, MouseEvent> onContextMenu) {
        this.onSelected = onSelected;
        this.onContextMenu = onContextMenu;
        strip.setOpaque(false);
    }

    JComponent strip() {
        return strip;
    }

    JComponent content() {
        return content;
    }

    TabPanel selected() {
        return selected;
    }

    void setTabs(List<Tab> tabs) {
        TabPanel previous = selected;
        strip.removeAll();
        content.removeAll();
        buttons.clear();
        for (Tab tab : tabs) {
            TabButton button = new TabButton(tab);
            buttons.add(button);
            strip.add(button);
            content.add(tab.panel(), key(tab.panel()));
        }
        boolean stillThere = tabs.stream().anyMatch(tab -> tab.panel() == previous);
        TabPanel next = stillThere ? previous : tabs.isEmpty() ? null : tabs.getFirst().panel();
        show(next);
        strip.revalidate();
        strip.repaint();
        content.revalidate();
        content.repaint();
        if (previous != null && next != previous && next != null) {
            onSelected.accept(next);
        }
    }

    private void select(TabPanel panel) {
        if (panel != selected) {
            show(panel);
            onSelected.accept(panel);
        }
    }

    private void show(TabPanel panel) {
        selected = panel;
        if (panel != null) {
            cards.show(content, key(panel));
        }
        buttons.forEach(Component::repaint);
    }

    private static String key(TabPanel panel) {
        return Integer.toHexString(System.identityHashCode(panel));
    }

    private void showPagesMenu(TabButton button, SubTabs subTabs) {
        if (subTabs == pagesMenuClosedFor && System.currentTimeMillis() - pagesMenuClosedAt < 300) {
            return;
        }
        JPopupMenu menu = new JPopupMenu();
        for (SubTabs.Page page : subTabs.visiblePages()) {
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(page.title(), page.icon(), page == subTabs.selectedPage());
            item.setToolTipText(page.toolTip());
            item.addActionListener(event -> subTabs.select(page));
            menu.add(item);
        }
        menu.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent event) {
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent event) {
                pagesMenuClosedFor = subTabs;
                pagesMenuClosedAt = System.currentTimeMillis();
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent event) {
            }
        });
        menu.show(button, 0, button.getHeight());
    }

    private static Color uiColor(String key, Color fallback) {
        Color color = UIManager.getColor(key);
        return color == null ? fallback : color;
    }

    private final class TabButton extends JPanel {

        private final Tab tab;
        private final JLabel title;
        private final JLabel arrow = new JLabel(Icons.DROPDOWN);
        private boolean hover;
        private boolean compact;

        TabButton(Tab tab) {
            super(new BorderLayout(ARROW_GAP, 0));
            this.tab = tab;
            title = new JLabel(tab.label(), tab.icon(), SwingConstants.LEADING);
            title.setIconTextGap(ICON_TEXT_GAP);
            Font font = UIManager.getFont("TabbedPane.font");
            if (font != null) {
                title.setFont(font);
            }
            title.setForeground(uiColor("Menu.foreground", title.getForeground()));
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(0, SIDE_PADDING, 0, SIDE_PADDING));
            add(title, BorderLayout.CENTER);
            arrow.setVisible(hasPages());
            add(arrow, BorderLayout.EAST);
            putClientProperty(CAPTION, false);
            title.putClientProperty(CAPTION, false);
            arrow.putClientProperty(CAPTION, false);
            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    if (e.isPopupTrigger()) {
                        onContextMenu.accept(tab.panel(), e);
                    } else if (SwingUtilities.isLeftMouseButton(e)) {
                        select(tab.panel());
                        if (hasPages() && tab.panel() instanceof SubTabs subTabs) {
                            showPagesMenu(TabButton.this, subTabs);
                        }
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (e.isPopupTrigger()) {
                        onContextMenu.accept(tab.panel(), e);
                    }
                }
            };
            addMouseListener(mouse);
        }

        boolean hasPages() {
            return tab.panel() instanceof SubTabs subTabs && subTabs.visiblePages().size() > 1;
        }

        void setCompact(boolean compact) {
            if (this.compact != compact) {
                this.compact = compact;
                title.setText(compact ? null : tab.label());
                setToolTipText(compact ? tab.label() : null);
            }
        }

        int widthFor(boolean compactWidth) {
            Insets insets = getInsets();
            int width = insets.left + insets.right + tab.icon().getIconWidth();
            if (!compactWidth) {
                width += ICON_TEXT_GAP + title.getFontMetrics(title.getFont()).stringWidth(tab.label());
            }
            if (hasPages()) {
                width += ARROW_GAP + Icons.DROPDOWN.getIconWidth();
            }
            return width;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(widthFor(compact), TAB_HEIGHT);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Color accent = uiColor("TabbedPane.underlineColor", Icons.accent());
            boolean isSelected = tab.panel() == selected;
            Color fill = null;
            if (isSelected) {
                fill = uiColor("TabbedPane.selectedBackground",
                        new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 22));
            } else if (hover) {
                fill = uiColor("TabbedPane.hoverColor", new Color(0, 0, 0, 14));
            }
            if (fill != null) {
                g.setColor(fill);
                g.fillRect(0, 0, getWidth(), getHeight());
            }
            if (isSelected) {
                g.setColor(accent);
                g.fillRect(2, getHeight() - UNDERLINE_HEIGHT, getWidth() - 4, UNDERLINE_HEIGHT);
            }
        }
    }

    private final class StripLayout implements LayoutManager {

        @Override
        public void addLayoutComponent(String name, Component component) {
        }

        @Override
        public void removeLayoutComponent(Component component) {
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            return new Dimension(totalWidth(false), TAB_HEIGHT);
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return new Dimension(totalWidth(true), TAB_HEIGHT);
        }

        @Override
        public void layoutContainer(Container parent) {
            boolean compact = parent.getWidth() < totalWidth(false);
            int height = parent.getHeight();
            int x = 0;
            for (TabButton button : buttons) {
                button.setCompact(compact);
                int width = button.widthFor(compact);
                button.setBounds(x, 0, width, height);
                x += width;
            }
        }

        private int totalWidth(boolean compact) {
            return buttons.stream().mapToInt(button -> button.widthFor(compact)).sum();
        }
    }
}
