package com.evefarm.ui;

import com.evefarm.db.dao.SettingsDao;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class TabVisibilityDialog extends JDialog {

    record Page(String key, String label) {
    }

    record Group(String key, String label, List<Page> pages) {

        boolean hasSubTabs() {
            return pages.size() > 1;
        }
    }

    private record Row(Group group, Page page) {
    }

    private static final int SUB_TAB_INDENT = 22;

    private final SettingsDao settingsDao;
    private final Runnable onSaved;
    private final List<Group> groups;
    private final Set<String> shown = new HashSet<>();
    private final DefaultListModel<Row> rows = new DefaultListModel<>();
    private final JList<Row> list = new JList<>(rows);

    TabVisibilityDialog(Window owner, SettingsDao settingsDao, List<Group> groupsInOrder, Runnable onSaved) {
        super(owner, "Show Tabs", ModalityType.APPLICATION_MODAL);
        this.settingsDao = settingsDao;
        this.onSaved = onSaved;
        this.groups = new ArrayList<>(groupsInOrder);

        Set<String> hidden = TabVisibility.readHidden(settingsDao);
        groups.forEach(group -> group.pages().stream().map(Page::key).filter(key -> !hidden.contains(key))
                .forEach(shown::add));
        fillRows(null);
        list.setCellRenderer((jList, row, index, isSelected, cellHasFocus) -> {
            JCheckBox box = new JCheckBox(row.page() == null || !row.group().hasSubTabs() ? row.group().label()
                    : row.page().label(), isShown(row));
            if (row.page() != null && row.group().hasSubTabs()) {
                box.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createEmptyBorder(0, SUB_TAB_INDENT, 0, 0), box.getBorder()));
            }
            box.setBackground(isSelected ? jList.getSelectionBackground() : jList.getBackground());
            box.setForeground(isSelected ? jList.getSelectionForeground() : jList.getForeground());
            return box;
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int index = list.locationToIndex(e.getPoint());
                if (index >= 0) {
                    toggle(rows.get(index));
                }
            }
        });
        list.setToolTipText("Tick the tabs to show. A tab with tabs inside shows only the ones you tick. "
                + "Move Up and Move Down change the order of the tab bar. Hidden tabs still refresh their data "
                + "with Update.");

        JButton up = new JButton("Move Up", Icons.MOVE_UP);
        up.addActionListener(e -> move(-1));
        JButton down = new JButton("Move Down", Icons.MOVE_DOWN);
        down.addActionListener(e -> move(1));
        JPanel sideButtons = new JPanel(new BorderLayout());
        sideButtons.add(ButtonSizing.column(4, up, down), BorderLayout.NORTH);

        JButton save = new JButton("Save", Icons.SAVE);
        save.addActionListener(e -> save());
        JButton cancel = new JButton("Cancel", Icons.CANCEL);
        cancel.addActionListener(e -> dispose());
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(ButtonSizing.row(6, save, cancel));

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(new JScrollPane(list), BorderLayout.CENTER);
        content.add(sideButtons, BorderLayout.EAST);
        content.add(buttonPanel, BorderLayout.SOUTH);
        setContentPane(content);

        setSize(380, 520);
        setLocationRelativeTo(owner);
    }

    private void fillRows(Row select) {
        rows.clear();
        for (Group group : groups) {
            if (group.hasSubTabs()) {
                rows.addElement(new Row(group, null));
            }
            group.pages().forEach(page -> rows.addElement(new Row(group, page)));
        }
        if (select != null) {
            list.setSelectedIndex(indexOf(select));
        }
    }

    private int indexOf(Row row) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).group().key().equals(row.group().key())
                    && Objects.equals(rows.get(i).page(), row.page())) {
                return i;
            }
        }
        return -1;
    }

    private boolean isShown(Row row) {
        return row.page() == null ? row.group().pages().stream().anyMatch(page -> shown.contains(page.key()))
                : shown.contains(row.page().key());
    }

    private void toggle(Row row) {
        if (row.page() != null) {
            if (!shown.remove(row.page().key())) {
                shown.add(row.page().key());
            }
        } else if (row.group().pages().stream().allMatch(page -> shown.contains(page.key()))) {
            row.group().pages().forEach(page -> shown.remove(page.key()));
        } else {
            row.group().pages().forEach(page -> shown.add(page.key()));
        }
        list.repaint();
    }

    private void move(int delta) {
        Row selected = list.getSelectedValue();
        if (selected == null) {
            return;
        }
        int index = groups.indexOf(selected.group());
        int target = index + delta;
        if (target < 0 || target >= groups.size()) {
            return;
        }
        groups.add(target, groups.remove(index));
        fillRows(selected);
    }

    private void save() {
        List<String> orderedKeys = new ArrayList<>();
        Set<String> hiddenKeys = new HashSet<>();
        for (Group group : groups) {
            orderedKeys.add(group.key());
            group.pages().stream().map(Page::key).filter(key -> !shown.contains(key)).forEach(hiddenKeys::add);
        }
        TabVisibility.writeOrder(settingsDao, orderedKeys);
        TabVisibility.writeHidden(settingsDao, hiddenKeys);
        dispose();
        onSaved.run();
    }
}
