package com.evefarm.ui;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.HierarchyBoundsAdapter;
import java.awt.event.HierarchyEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Function;

final class SuggestionPopup<T> {

    private static final int ROW_PADDING = 3;
    private static final int WIDTH_SLACK = 12;

    private final JTextField field;
    private final Function<String, List<T>> suggestions;
    private final Function<T, String> label;
    private final DefaultListModel<T> model = new DefaultListModel<>();
    private final JList<T> list = new JList<>(model);
    private Popup popup;
    private boolean filling;

    private SuggestionPopup(JTextField field, Function<String, List<T>> suggestions, Function<T, String> label,
                            Function<T, String> detail) {
        this.field = field;
        this.suggestions = suggestions;
        this.label = label;
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFocusable(false);
        list.setCellRenderer(new Row(detail));
        Border border = UIManager.getBorder("PopupMenu.border");
        list.setBorder(border != null ? border : BorderFactory.createLineBorder(UiColors.muted()));
    }

    static <T> void install(JTextField field, Function<String, List<T>> suggestions, Function<T, String> label,
                            Function<T, String> detail) {
        new SuggestionPopup<>(field, suggestions, label, detail).listen();
    }

    private void listen() {
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                typed();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                typed();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
            }
        });
        field.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (popup != null) {
                    useKey(e);
                } else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    refresh();
                }
            }
        });
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                hide();
            }
        });
        field.addHierarchyBoundsListener(new HierarchyBoundsAdapter() {
            @Override
            public void ancestorMoved(HierarchyEvent e) {
                hide();
            }

            @Override
            public void ancestorResized(HierarchyEvent e) {
                hide();
            }
        });
        field.addHierarchyListener(e -> {
            if (!field.isShowing()) {
                hide();
            }
        });
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int index = rowAt(e.getPoint());
                if (index >= 0) {
                    list.setSelectedIndex(index);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                int index = rowAt(e.getPoint());
                if (index >= 0) {
                    accept(model.get(index));
                }
            }
        };
        list.addMouseListener(mouse);
        list.addMouseMotionListener(mouse);
    }

    private int rowAt(Point point) {
        int index = list.locationToIndex(point);
        Rectangle cell = index < 0 ? null : list.getCellBounds(index, index);
        return cell != null && cell.contains(point) ? index : -1;
    }

    private void typed() {
        if (!filling && field.isFocusOwner()) {
            SwingUtilities.invokeLater(this::refresh);
        }
    }

    private void useKey(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_DOWN -> move(1);
            case KeyEvent.VK_UP -> move(-1);
            case KeyEvent.VK_ENTER -> accept(list.getSelectedValue());
            case KeyEvent.VK_ESCAPE -> hide();
            default -> {
                return;
            }
        }
        e.consume();
    }

    private void move(int step) {
        list.setSelectedIndex(Math.clamp(list.getSelectedIndex() + step, 0, model.size() - 1));
    }

    private void refresh() {
        String text = field.getText().strip();
        List<T> found = text.isEmpty() ? List.of() : suggestions.apply(text);
        if (found.isEmpty() || !field.isShowing()
                || (found.size() == 1 && label.apply(found.getFirst()).equalsIgnoreCase(text))) {
            hide();
            return;
        }
        model.clear();
        model.addAll(found);
        list.setSelectedIndex(0);
        list.setFixedCellHeight(field.getFontMetrics(list.getFont()).getHeight() + 2 * ROW_PADDING);
        list.setPreferredSize(null);
        Dimension needed = list.getPreferredSize();
        Dimension size = new Dimension(Math.max(needed.width + WIDTH_SLACK, field.getWidth()), needed.height);
        list.setPreferredSize(size);
        if (popup != null && size.equals(list.getSize())) {
            return;
        }
        hide();
        Point at = field.getLocationOnScreen();
        popup = PopupFactory.getSharedInstance().getPopup(field, list, at.x, at.y + field.getHeight());
        popup.show();
    }

    private void hide() {
        if (popup != null) {
            popup.hide();
            popup = null;
        }
    }

    private void accept(T chosen) {
        hide();
        if (chosen != null) {
            filling = true;
            try {
                field.setText(label.apply(chosen));
            } finally {
                filling = false;
            }
        }
        field.postActionEvent();
    }

    private final class Row extends JPanel implements ListCellRenderer<T> {

        private final Function<T, String> detail;
        private final JLabel name = new JLabel();
        private final JLabel extra = new JLabel();

        private Row(Function<T, String> detail) {
            super(new BorderLayout(24, 0));
            this.detail = detail;
            setBorder(BorderFactory.createEmptyBorder(ROW_PADDING, 8, ROW_PADDING, 8));
            name.putClientProperty("html.disable", Boolean.TRUE);
            extra.putClientProperty("html.disable", Boolean.TRUE);
            add(name, BorderLayout.CENTER);
            add(extra, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends T> source, T value, int index,
                                                      boolean selected, boolean focused) {
            name.setText(label.apply(value));
            extra.setText(detail.apply(value));
            name.setFont(source.getFont());
            extra.setFont(source.getFont());
            setBackground(selected ? source.getSelectionBackground() : source.getBackground());
            name.setForeground(selected ? source.getSelectionForeground() : source.getForeground());
            extra.setForeground(selected ? source.getSelectionForeground() : UiColors.muted());
            return this;
        }
    }
}
