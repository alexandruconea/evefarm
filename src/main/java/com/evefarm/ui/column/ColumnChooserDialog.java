package com.evefarm.ui.column;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import com.evefarm.ui.ButtonSizing;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ColumnChooserDialog extends JDialog {

    private record Entry(String key, String label) {
    }

    private final DefaultListModel<Entry> listModel = new DefaultListModel<>();
    private final JList<Entry> list = new JList<>(listModel);
    private final Set<String> checked = new LinkedHashSet<>();
    private boolean confirmed = false;

    private ColumnChooserDialog(Window owner, List<Entry> allEntries, List<String> visibleKeysInOrder) {
        super(owner, "Choose Columns", ModalityType.APPLICATION_MODAL);

        checked.addAll(visibleKeysInOrder);
        Map<String, Entry> byKey = new LinkedHashMap<>();
        for (Entry entry : allEntries) {
            byKey.put(entry.key(), entry);
        }
        for (String key : visibleKeysInOrder) {
            Entry entry = byKey.remove(key);
            if (entry != null) {
                listModel.addElement(entry);
            }
        }
        for (Entry entry : byKey.values()) {
            listModel.addElement(entry);
        }

        list.setCellRenderer((jList, entry, index, isSelected, cellHasFocus) -> {
            JCheckBox box = new JCheckBox(entry.label(), checked.contains(entry.key()));
            box.setBackground(isSelected ? jList.getSelectionBackground() : jList.getBackground());
            box.setForeground(isSelected ? jList.getSelectionForeground() : jList.getForeground());
            return box;
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int index = list.locationToIndex(e.getPoint());
                if (index < 0) {
                    return;
                }
                Entry entry = listModel.get(index);
                if (!checked.remove(entry.key())) {
                    checked.add(entry.key());
                }
                list.repaint();
            }
        });

        JButton up = new JButton("Move Up");
        up.addActionListener(e -> move(-1));
        JButton down = new JButton("Move Down");
        down.addActionListener(e -> move(1));
        JButton showAll = new JButton("Show All");
        showAll.addActionListener(e -> {
            for (int i = 0; i < listModel.size(); i++) {
                checked.add(listModel.get(i).key());
            }
            list.repaint();
        });
        JButton hideAll = new JButton("Hide All");
        hideAll.addActionListener(e -> {
            checked.clear();
            list.repaint();
        });
        JPanel sideButtons = ButtonSizing.column(4, up, down, showAll, hideAll);

        JButton ok = new JButton("OK");
        ok.addActionListener(e -> {
            confirmed = true;
            dispose();
        });
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dispose());
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.add(ButtonSizing.row(6, ok, cancel));

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(new JLabel("<html>Check the columns to show.<br>Drag column headers in the "
                + "table to reorder them, or use Move Up/Down here.</html>"), BorderLayout.NORTH);
        content.add(new JScrollPane(list), BorderLayout.CENTER);
        content.add(sideButtons, BorderLayout.EAST);
        content.add(bottom, BorderLayout.SOUTH);
        setContentPane(content);

        setSize(420, 460);
        setLocationRelativeTo(owner);
    }

    private void move(int delta) {
        int index = list.getSelectedIndex();
        int target = index + delta;
        if (index < 0 || target < 0 || target >= listModel.size()) {
            return;
        }
        Entry entry = listModel.remove(index);
        listModel.add(target, entry);
        list.setSelectedIndex(target);
    }

    public static <T> List<String> show(Window owner, List<ColumnDef<T>> allColumns, List<String> visibleKeysInOrder) {
        List<Entry> entries = new ArrayList<>();
        for (ColumnDef<T> def : allColumns) {
            entries.add(new Entry(def.key(), def.label()));
        }
        ColumnChooserDialog dialog = new ColumnChooserDialog(owner, entries, visibleKeysInOrder);
        dialog.setVisible(true);
        if (!dialog.confirmed) {
            return null;
        }
        List<String> result = new ArrayList<>();
        for (int i = 0; i < dialog.listModel.size(); i++) {
            Entry entry = dialog.listModel.get(i);
            if (dialog.checked.contains(entry.key())) {
                result.add(entry.key());
            }
        }
        return result;
    }
}
