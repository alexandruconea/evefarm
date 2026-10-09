package com.evefarm.ui;

import com.evefarm.AppContext;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;

final class AbyssIgnoredItemsDialog extends JDialog {

    private record Item(int typeId, String name) {
        @Override
        public String toString() {
            return name;
        }
    }

    private final AppContext appContext;
    private final DefaultListModel<Item> model = new DefaultListModel<>();
    private final JList<Item> list = new JList<>(model);
    private final JButton stopButton = new JButton("Stop Ignoring", Icons.REMOVE);
    private boolean changed;

    private AbyssIgnoredItemsDialog(Window owner, AppContext appContext) {
        super(owner, "Items Ignored as Loot", ModalityType.APPLICATION_MODAL);
        this.appContext = appContext;
        buildUi();
        reload();
        pack();
        setLocationRelativeTo(owner);
    }

    static boolean showDialog(Window owner, AppContext appContext) {
        AbyssIgnoredItemsDialog dialog = new AbyssIgnoredItemsDialog(owner, appContext);
        dialog.setVisible(true);
        return dialog.changed;
    }

    private void buildUi() {
        list.setToolTipText("These items are never counted as Abyss loot, for example your own ammo. To add one, "
                + "right-click it in the loot of a run and choose Always Ignore.");
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.addListSelectionListener(e -> stopButton.setEnabled(!list.isSelectionEmpty()));
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(380, 220));

        stopButton.setEnabled(false);
        stopButton.addActionListener(e -> stopIgnoring());
        JButton closeButton = new JButton("Close", Icons.CANCEL);
        closeButton.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttons.add(ButtonSizing.row(6, stopButton, closeButton));

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(scroll, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
    }

    private void reload() {
        model.clear();
        appContext.abyssalRunDao.listIgnoredItems().forEach((typeId, name) -> model.addElement(new Item(typeId, name)));
    }

    private void stopIgnoring() {
        for (Item item : list.getSelectedValuesList()) {
            appContext.abyssalRunDao.unignoreItem(item.typeId());
            changed = true;
        }
        reload();
    }
}
