package com.evefarm.ui;

import com.evefarm.db.dao.SettingsDao;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TabVisibilityDialog extends JDialog {

    private record Entry(String key, String label) {
    }

    private final SettingsDao settingsDao;
    private final DefaultListModel<Entry> listModel = new DefaultListModel<>();
    private final JList<Entry> list = new JList<>(listModel);
    private final Set<String> checked = new LinkedHashSet<>();
    private final Runnable onSaved;

    public TabVisibilityDialog(Window owner, SettingsDao settingsDao, Map<String, String> tabKeysToLabelsInOrder,
                                Runnable onSaved) {
        super(owner, "Show Tabs", ModalityType.APPLICATION_MODAL);
        this.settingsDao = settingsDao;
        this.onSaved = onSaved;

        Set<String> hidden = TabVisibility.readHidden(settingsDao);
        for (Map.Entry<String, String> entry : tabKeysToLabelsInOrder.entrySet()) {
            if (!hidden.contains(entry.getKey())) {
                checked.add(entry.getKey());
            }
            listModel.addElement(new Entry(entry.getKey(), entry.getValue()));
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
        JPanel sideButtons = ButtonSizing.column(4, up, down);

        JButton save = new JButton("Save", Icons.SAVE);
        save.addActionListener(e -> save());
        JButton cancel = new JButton("Cancel", Icons.CANCEL);
        cancel.addActionListener(e -> dispose());
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(ButtonSizing.row(6, save, cancel));

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(new JLabel("<html>Check the tabs to show.<br>Use Move Up/Down to change the "
                + "tab bar's order.<br>Hidden tabs' data still refreshes normally via "
                + "Update.</html>"), BorderLayout.NORTH);
        content.add(new JScrollPane(list), BorderLayout.CENTER);
        content.add(sideButtons, BorderLayout.EAST);
        content.add(buttonPanel, BorderLayout.SOUTH);
        setContentPane(content);

        setSize(360, 420);
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

    private void save() {
        List<String> orderedKeys = new ArrayList<>();
        List<String> hiddenKeys = new ArrayList<>();
        for (int i = 0; i < listModel.size(); i++) {
            Entry entry = listModel.get(i);
            orderedKeys.add(entry.key());
            if (!checked.contains(entry.key())) {
                hiddenKeys.add(entry.key());
            }
        }
        TabVisibility.writeOrder(settingsDao, orderedKeys);
        TabVisibility.writeHidden(settingsDao, Set.copyOf(hiddenKeys));
        dispose();
        onSaved.run();
    }
}
