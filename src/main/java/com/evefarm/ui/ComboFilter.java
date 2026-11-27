package com.evefarm.ui;

import javax.accessibility.Accessible;
import javax.swing.BorderFactory;
import javax.swing.ComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.plaf.basic.ComboPopup;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public final class ComboFilter {

    static final int MIN_ITEMS = 12;
    private static final String KEY = "evefarm.comboFilter";
    private static final String HINT = "Type to filter";
    private static final int ICON_SIZE = 13;
    private static final JList<Object> PROBE = new JList<>();

    private final JComboBox<?> combo;
    private final JLabel header = new JLabel();
    private boolean wrapping;

    public static <E> void install(JComboBox<E> combo) {
        if (combo.getClientProperty(KEY) == null) {
            combo.putClientProperty(KEY, new ComboFilter(combo));
        }
    }

    private <E> ComboFilter(JComboBox<E> combo) {
        this.combo = combo;
        wrap(combo);
        combo.addPropertyChangeListener("model", e -> {
            if (!wrapping && !(e.getNewValue() instanceof FilteredComboModel)) {
                if (e.getOldValue() instanceof FilteredComboModel<?> old) {
                    old.detach();
                }
                wrap(combo);
            }
        });
        JComboBox.KeySelectionManager previous = combo.getKeySelectionManager();
        combo.setKeySelectionManager((key, model) -> active() || previous == null ? -1
                : previous.selectionForKey(key, model));
        combo.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                pressed(e);
            }

            @Override
            public void keyTyped(KeyEvent e) {
                typed(e);
            }
        });
        combo.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                attachHeader();
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                FilteredComboModel<?> model = model();
                if (model != null && !model.filter().isEmpty()) {
                    model.setFilter("");
                }
                updateHeader();
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
            }
        });
        header.setIcon(FontAwesome.icon("magnifying-glass", UiColors::muted, ICON_SIZE));
        header.setIconTextGap(6);
        header.putClientProperty("html.disable", Boolean.TRUE);
        header.setAlignmentX(Component.CENTER_ALIGNMENT);
    }

    private <E> void wrap(JComboBox<E> target) {
        ComboBoxModel<E> source = target.getModel();
        wrapping = true;
        try {
            target.setModel(new FilteredComboModel<>(source, this::textOf));
        } finally {
            wrapping = false;
        }
    }

    private FilteredComboModel<?> model() {
        return combo.getModel() instanceof FilteredComboModel<?> model ? model : null;
    }

    private boolean active() {
        FilteredComboModel<?> model = model();
        return combo.isEnabled() && model != null && model.source().getSize() >= MIN_ITEMS;
    }

    private void pressed(KeyEvent e) {
        if (!active() || e.isConsumed()) {
            return;
        }
        String filter = model().filter();
        if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
            if (!filter.isEmpty()) {
                type(filter.substring(0, filter.length() - 1));
            }
            e.consume();
        } else if (e.getKeyCode() == KeyEvent.VK_SPACE && !filter.isEmpty()) {
            e.consume();
        }
    }

    private void typed(KeyEvent e) {
        if (!active() || e.isConsumed()) {
            return;
        }
        char c = e.getKeyChar();
        if (c == KeyEvent.CHAR_UNDEFINED || Character.isISOControl(c) || e.isControlDown()
                || e.isAltDown() && !e.isAltGraphDown()) {
            return;
        }
        String filter = model().filter();
        if (c != ' ' || !filter.isEmpty()) {
            type(filter + c);
        }
        e.consume();
    }

    private void type(String text) {
        model().setFilter(text);
        if (combo.isPopupVisible()) {
            refit();
        } else if (combo.isShowing()) {
            combo.showPopup();
        }
        highlightFirst();
        updateHeader();
    }

    private ComboPopup popup() {
        Accessible child = combo.getUI().getAccessibleChild(combo, 0);
        return child instanceof ComboPopup popup ? popup : null;
    }

    private void attachHeader() {
        ComboPopup popup = popup();
        if (popup instanceof JPopupMenu menu && header.getParent() != menu) {
            menu.insert(header, 0);
        }
        updateHeader();
    }

    private void updateHeader() {
        FilteredComboModel<?> model = model();
        String filter = model == null ? "" : model.filter();
        boolean shown = active();
        header.setVisible(shown);
        if (!shown) {
            return;
        }
        Color separator = UIManager.getColor("Separator.foreground");
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, separator == null ? Color.GRAY : separator),
                BorderFactory.createEmptyBorder(4, 10, 6, 10)));
        if (filter.isEmpty()) {
            header.setText(HINT);
            header.setForeground(UiColors.muted());
        } else {
            header.setText(model.getSize() == 0 ? filter + "  -  no match" : filter);
            header.setForeground(UIManager.getColor("Label.foreground"));
        }
        header.setMaximumSize(new Dimension(Short.MAX_VALUE, header.getPreferredSize().height));
        header.revalidate();
        header.repaint();
    }

    private void highlightFirst() {
        ComboPopup popup = popup();
        if (popup == null) {
            return;
        }
        JList<?> list = popup.getList();
        if (list.getModel().getSize() > 0) {
            list.setSelectedIndex(0);
            list.ensureIndexIsVisible(0);
        } else {
            list.clearSelection();
        }
    }

    private void refit() {
        ComboPopup popup = popup();
        if (!(popup instanceof JPopupMenu menu)) {
            return;
        }
        JList<?> list = popup.getList();
        if (!(SwingUtilities.getAncestorOfClass(JScrollPane.class, list) instanceof JScrollPane scroller)) {
            return;
        }
        int rows = Math.min(combo.getMaximumRowCount(), list.getModel().getSize());
        int height = 0;
        for (int i = 0; i < rows; i++) {
            height += list.getCellBounds(i, i).height;
        }
        if (height == 0) {
            height = combo.getHeight();
        }
        Insets insets = scroller.getInsets();
        height += insets.top + insets.bottom;
        if (scroller.getViewportBorder() != null) {
            Insets viewport = scroller.getViewportBorder().getBorderInsets(scroller);
            height += viewport.top + viewport.bottom;
        }
        Dimension size = new Dimension(scroller.getWidth(), height);
        scroller.setPreferredSize(size);
        scroller.setMinimumSize(size);
        scroller.setMaximumSize(size);
        list.revalidate();
        menu.pack();
    }

    @SuppressWarnings("unchecked")
    private String textOf(Object value) {
        if (value == null) {
            return "";
        }
        ListCellRenderer<Object> renderer = (ListCellRenderer<Object>) combo.getRenderer();
        if (renderer != null) {
            Component c = renderer.getListCellRendererComponent(PROBE, value, 0, false, false);
            if (c instanceof JLabel label && label.getText() != null) {
                return label.getText().replaceAll("<[^>]*>", "");
            }
        }
        return value.toString();
    }
}
