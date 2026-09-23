package com.evefarm.ui;

import com.evefarm.AppContext;
import com.evefarm.auth.CharacterIdentity;
import com.evefarm.model.EveCharacter;
import com.evefarm.util.DateUtil;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class CharactersDialog extends JDialog {

    private static final Logger LOG = Logger.getLogger(CharactersDialog.class.getName());

    private final AppContext appContext;
    private final Runnable onChanged;
    private final CharacterTableModel tableModel = new CharacterTableModel();

    private JButton addButton;
    private JTable table;
    private SwingWorker<CharacterIdentity, Void> loginWorker;

    public CharactersDialog(Window owner, AppContext appContext, Runnable onChanged) {
        super(owner, "Characters", ModalityType.APPLICATION_MODAL);
        this.appContext = appContext;
        this.onChanged = onChanged;
        buildUi();
        reloadCharacters();
        pack();
        setLocationRelativeTo(owner);
    }

    private void buildUi() {
        table = new JTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        TableStyler.style(table);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(480, 220));

        addButton = new JButton("Add Character...", Icons.ADD);
        addButton.addActionListener(e -> onAddCharacter());
        JButton removeButton = new JButton("Remove", Icons.REMOVE);
        removeButton.addActionListener(e -> onRemoveCharacter());

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolbar.add(ButtonSizing.row(4, addButton, removeButton));

        JButton closeButton = new JButton("Close", Icons.CANCEL);
        closeButton.addActionListener(e -> {
            cancelPendingLogin();
            dispose();
        });
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomPanel.add(closeButton);

        setLayout(new BorderLayout());
        add(toolbar, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void onAddCharacter() {
        if (loginWorker != null && !loginWorker.isDone()) {
            cancelPendingLogin();
            return;
        }

        addButton.setText("Cancel Login");
        addButton.setIcon(Icons.CANCEL);

        loginWorker = new SwingWorker<CharacterIdentity, Void>() {
            @Override
            protected CharacterIdentity doInBackground() throws Exception {
                return appContext.characterService.addCharacter();
            }

            @Override
            protected void done() {
                addButton.setText("Add Character...");
                addButton.setIcon(Icons.ADD);
                if (isCancelled()) {
                    return;
                }
                try {
                    CharacterIdentity identity = get();
                    JOptionPane.showMessageDialog(CharactersDialog.this,
                            "Logged in as " + identity.characterName(),
                            "Success", JOptionPane.INFORMATION_MESSAGE);
                    reloadCharacters();
                    onChanged.run();
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    LOG.log(Level.WARNING, "Adding a character failed", cause);
                    JOptionPane.showMessageDialog(CharactersDialog.this,
                            "Login failed: " + cause.getMessage(),
                            "Login failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        loginWorker.execute();
    }

    private void cancelPendingLogin() {
        if (loginWorker != null && !loginWorker.isDone()) {
            loginWorker.cancel(true);
        }
    }

    private void onRemoveCharacter() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow < 0) {
            return;
        }
        EveCharacter selected = tableModel.rowAt(table.convertRowIndexToModel(selectedRow));
        int confirm = JOptionPane.showConfirmDialog(this,
                "Remove " + selected.characterName() + "?\n\n"
                        + "Its login token and current assets, orders, contracts and jobs are removed.\n"
                        + "Its history - journal, transactions, Tracker, NPC kills and officers - is kept,\n"
                        + "hidden until you add the character again.",
                "Confirm removal", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            appContext.characterService.removeCharacter(selected.characterId());
            reloadCharacters();
            onChanged.run();
        }
    }

    private void reloadCharacters() {
        tableModel.setRows(appContext.characterService.listCharacters());
        TableStyler.packColumns(table);
    }

    private static final class CharacterTableModel extends AbstractTableModel {
        private final String[] columns = {"Character", "Character ID", "Added"};
        private List<EveCharacter> rows = new ArrayList<>();

        void setRows(List<EveCharacter> rows) {
            this.rows = rows;
            fireTableDataChanged();
        }

        EveCharacter rowAt(int index) {
            return rows.get(index);
        }

        @Override
        public int getRowCount() {
            return rows.size();
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
            EveCharacter character = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> character.characterName();
                case 1 -> character.characterId();
                case 2 -> DateUtil.format(character.addedAt());
                default -> "";
            };
        }
    }
}
