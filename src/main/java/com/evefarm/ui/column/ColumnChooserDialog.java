package com.evefarm.ui.column;

import com.evefarm.ui.ButtonSizing;
import com.evefarm.ui.CheckList;
import com.evefarm.ui.Icons;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ColumnChooserDialog extends JDialog {

    private record Entry(String key, String label, String description) {
    }

    private final CheckList<Entry> entries;
    private boolean confirmed = false;

    private ColumnChooserDialog(Window owner, List<Entry> allEntries, List<String> visibleKeysInOrder) {
        super(owner, "Choose Columns", ModalityType.APPLICATION_MODAL);

        Map<String, Entry> byKey = new LinkedHashMap<>();
        for (Entry entry : allEntries) {
            byKey.put(entry.key(), entry);
        }
        List<Entry> ordered = new ArrayList<>();
        for (String key : visibleKeysInOrder) {
            Entry entry = byKey.remove(key);
            if (entry != null) {
                ordered.add(entry);
            }
        }
        ordered.addAll(byKey.values());
        entries = new CheckList<>(ordered, visibleKeysInOrder, Entry::key, Entry::label, Entry::description);
        JList<Entry> list = entries.list();

        JButton up = new JButton("Move Up", Icons.MOVE_UP);
        up.setToolTipText("Move the selected column to the left in the table. You can also drag the column headers");
        up.addActionListener(e -> entries.move(-1));
        JButton down = new JButton("Move Down", Icons.MOVE_DOWN);
        down.setToolTipText("Move the selected column to the right in the table. You can also drag the column "
                + "headers");
        down.addActionListener(e -> entries.move(1));
        JButton showAll = new JButton("Show All");
        showAll.addActionListener(e -> entries.checkAll(true));
        JButton hideAll = new JButton("Hide All");
        hideAll.addActionListener(e -> entries.checkAll(false));
        JPanel sideButtons = new JPanel(new BorderLayout());
        sideButtons.add(ButtonSizing.column(4, up, down, showAll, hideAll), BorderLayout.NORTH);

        JButton ok = new JButton("OK");
        ok.addActionListener(e -> {
            confirmed = true;
            dispose();
        });
        JButton cancel = new JButton("Cancel", Icons.CANCEL);
        cancel.addActionListener(e -> dispose());
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.add(ButtonSizing.row(6, ok, cancel));

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        list.setToolTipText("Tick the columns to show. Drag the column headers in the table to reorder them, or "
                + "use Move Up and Move Down here.");
        content.add(new JScrollPane(list), BorderLayout.CENTER);
        content.add(sideButtons, BorderLayout.EAST);
        content.add(bottom, BorderLayout.SOUTH);
        setContentPane(content);

        setSize(420, 460);
        setLocationRelativeTo(owner);
    }

    public static <T> List<String> show(Window owner, List<ColumnDef<T>> allColumns, List<String> visibleKeysInOrder) {
        List<Entry> entries = new ArrayList<>();
        for (ColumnDef<T> def : allColumns) {
            entries.add(new Entry(def.key(), def.label(), def.description()));
        }
        ColumnChooserDialog dialog = new ColumnChooserDialog(owner, entries, visibleKeysInOrder);
        dialog.setVisible(true);
        if (!dialog.confirmed) {
            return null;
        }
        List<String> result = new ArrayList<>();
        for (Entry entry : dialog.entries.items()) {
            if (dialog.entries.isChecked(entry)) {
                result.add(entry.key());
            }
        }
        return result;
    }
}
