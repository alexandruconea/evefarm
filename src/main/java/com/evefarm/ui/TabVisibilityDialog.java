package com.evefarm.ui;

import com.evefarm.db.dao.SettingsDao;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TabVisibilityDialog extends JDialog {

    private record Entry(String key, String label) {
    }

    private final SettingsDao settingsDao;
    private final CheckList<Entry> entries;
    private final Runnable onSaved;

    public TabVisibilityDialog(Window owner, SettingsDao settingsDao, Map<String, String> tabKeysToLabelsInOrder,
                                Runnable onSaved) {
        super(owner, "Show Tabs", ModalityType.APPLICATION_MODAL);
        this.settingsDao = settingsDao;
        this.onSaved = onSaved;

        Set<String> hidden = TabVisibility.readHidden(settingsDao);
        List<Entry> tabs = new ArrayList<>();
        tabKeysToLabelsInOrder.forEach((key, label) -> tabs.add(new Entry(key, label)));
        entries = new CheckList<>(tabs, tabs.stream().map(Entry::key).filter(key -> !hidden.contains(key)).toList(),
                Entry::key, Entry::label, entry -> null);
        JList<Entry> list = entries.list();
        list.setToolTipText("Tick the tabs to show. Move Up and Move Down change the order of the tab bar. "
                + "Hidden tabs still refresh their data with Update.");

        JButton up = new JButton("Move Up", Icons.MOVE_UP);
        up.addActionListener(e -> entries.move(-1));
        JButton down = new JButton("Move Down", Icons.MOVE_DOWN);
        down.addActionListener(e -> entries.move(1));
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

        setSize(360, 420);
        setLocationRelativeTo(owner);
    }

    private void save() {
        List<String> orderedKeys = new ArrayList<>();
        List<String> hiddenKeys = new ArrayList<>();
        for (Entry entry : entries.items()) {
            orderedKeys.add(entry.key());
            if (!entries.isChecked(entry)) {
                hiddenKeys.add(entry.key());
            }
        }
        TabVisibility.writeOrder(settingsDao, orderedKeys);
        TabVisibility.writeHidden(settingsDao, Set.copyOf(hiddenKeys));
        dispose();
        onSaved.run();
    }
}
