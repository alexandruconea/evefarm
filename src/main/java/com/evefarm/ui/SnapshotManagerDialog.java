package com.evefarm.ui;

import com.evefarm.AppContext;
import com.evefarm.model.EveCharacter;
import com.evefarm.model.TrackerSnapshot;
import com.evefarm.util.DateUtil;
import com.evefarm.util.IskFormatter;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class SnapshotManagerDialog extends JDialog {

    private final AppContext appContext;
    private final List<TrackerSnapshot> snapshots;
    private final Map<Long, String> characterNames;
    private final SnapshotTableModel tableModel;
    private final Runnable onChanged;

    public SnapshotManagerDialog(Window owner, AppContext appContext, List<TrackerSnapshot> snapshots,
                                  Runnable onChanged) {
        super(owner, "Manage Snapshots", ModalityType.APPLICATION_MODAL);
        this.appContext = appContext;
        this.snapshots = new ArrayList<>(snapshots);
        this.onChanged = onChanged;
        this.characterNames = appContext.characterService.listCharacters().stream()
                .collect(Collectors.toMap(EveCharacter::characterId, EveCharacter::characterName));
        this.tableModel = new SnapshotTableModel();
        buildUi();
        pack();
        setLocationRelativeTo(owner);
    }

    private void buildUi() {
        JTable table = new JTable(tableModel);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        TableStyler.style(table);
        TableStyler.packColumns(table);
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(520, 320));

        JButton deleteButton = new JButton("Delete Selected", Icons.REMOVE);
        deleteButton.addActionListener(e -> deleteSelected(table));
        JButton closeButton = new JButton("Close", Icons.CANCEL);
        closeButton.addActionListener(e -> dispose());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(ButtonSizing.row(6, deleteButton, closeButton));

        setLayout(new BorderLayout());
        add(scrollPane, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void deleteSelected(JTable table) {
        int[] selectedRows = table.getSelectedRows();
        if (selectedRows.length == 0) {
            return;
        }
        int result = JOptionPane.showConfirmDialog(this,
                "Permanently delete " + selectedRows.length + " snapshot(s)? This cannot be undone.",
                "Delete Snapshots", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return;
        }
        List<TrackerSnapshot> toDelete = new ArrayList<>();
        for (int row : selectedRows) {
            toDelete.add(snapshots.get(table.convertRowIndexToModel(row)));
        }
        for (TrackerSnapshot snapshot : toDelete) {
            appContext.snapshotDao.delete(snapshot.characterId(), snapshot.capturedAt());
        }
        snapshots.removeAll(toDelete);
        tableModel.fireTableDataChanged();
        onChanged.run();
    }

    private final class SnapshotTableModel extends AbstractTableModel {
        private final String[] columns = {"Character", "Captured At", "Total"};

        @Override
        public int getRowCount() {
            return snapshots.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            TrackerSnapshot snapshot = snapshots.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> characterNames.getOrDefault(snapshot.characterId(), "#" + snapshot.characterId());
                case 1 -> DateUtil.formatWithSeconds(snapshot.capturedAt());
                case 2 -> IskFormatter.format(snapshot.totalValue());
                default -> "";
            };
        }
    }
}
