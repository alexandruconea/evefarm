package com.evefarm.ui;

import com.evefarm.AppContext;
import com.evefarm.auth.CharacterIdentity;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.model.Corporation;
import com.evefarm.model.CorporationAccess;
import com.evefarm.model.EveCharacter;
import com.evefarm.service.CorporationService;
import com.evefarm.ui.column.ColumnDescriptions;
import com.evefarm.ui.column.ColumnWidthMemory;
import com.evefarm.util.DateUtil;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class CharactersDialog extends JDialog {

    private static final Logger LOG = Logger.getLogger(CharactersDialog.class.getName());

    private final AppContext appContext;
    private final Runnable onChanged;
    private final CharacterTableModel tableModel = new CharacterTableModel();

    private final JCheckBox corporationBox = new JCheckBox("Also read the corporation");
    private JButton addButton;
    private JButton mainButton;
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
        table.setRowHeight(MainCharacterMarks.LARGE_PORTRAIT_SIZE + 8);
        ColumnWidthMemory.install(table, "characters", appContext.tableColumnStateDao);
        DefaultTableCellRenderer nameRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                EveCharacter character = tableModel.rowAt(t.convertRowIndexToModel(row));
                setIcon(IconRow.of(MainCharacterMarks.iconFor(tableModel.mainCharacterId, character.characterId()),
                        appContext.characterPortraits.portrait(character.characterId(),
                                MainCharacterMarks.LARGE_PORTRAIT_SIZE)));
                setIconTextGap(8);
                if (!isSelected) {
                    setBackground(TableStyler.rowBackground(t, row));
                }
                return this;
            }
        };
        nameRenderer.putClientProperty("html.disable", Boolean.TRUE);
        table.getColumnModel().getColumn(0).setCellRenderer(nameRenderer);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(480, 220));

        addButton = new JButton("Add Character...", Icons.ADD);
        addButton.addActionListener(e -> onAddCharacter());
        JButton removeButton = new JButton("Remove", Icons.REMOVE);
        removeButton.addActionListener(e -> onRemoveCharacter());
        mainButton = new JButton("Set as Main", Icons.MAIN);
        mainButton.setToolTipText("The main character is listed first and selected first in Values and LP Store");
        mainButton.setEnabled(false);
        mainButton.addActionListener(e -> onSetMain());
        JButton copySettingsButton = new JButton("Copy EVE Settings...", Icons.SWAP);
        copySettingsButton.setToolTipText("Copy EVE window positions and UI settings between characters");
        copySettingsButton.addActionListener(e -> onCopySettings());
        table.getSelectionModel().addListSelectionListener(e -> updateMainButton());

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolbar.add(ButtonSizing.row(4, addButton, removeButton, mainButton, copySettingsButton));
        corporationBox.setToolTipText("Also lets EVE Farm read your corporation's wallets, assets, market orders, "
                + "contracts, industry jobs and blueprints, as far as the character's corporation roles allow. For a "
                + "character you already have, tick it and add the character again. It is ticked at first and stays "
                + "as you leave it");
        corporationBox.setSelected("true".equals(appContext.settingsDao.getOrDefault(
                SettingsDao.ADD_WITH_CORPORATION, "true")));
        corporationBox.addActionListener(e -> appContext.settingsDao.set(SettingsDao.ADD_WITH_CORPORATION,
                String.valueOf(corporationBox.isSelected())));
        toolbar.add(corporationBox);

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

        boolean corporation = corporationBox.isSelected();
        loginWorker = new SwingWorker<CharacterIdentity, Void>() {
            @Override
            protected CharacterIdentity doInBackground() throws Exception {
                return appContext.characterService.addCharacter(corporation);
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

    private void onSetMain() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow < 0) {
            return;
        }
        EveCharacter selected = tableModel.rowAt(table.convertRowIndexToModel(selectedRow));
        appContext.characterService.setMainCharacter(selected.characterId());
        reloadCharacters();
        table.changeSelection(0, 0, false, false);
        onChanged.run();
    }

    private void onCopySettings() {
        int selectedRow = table.getSelectedRow();
        Long preferredSourceId = selectedRow < 0 ? null
                : tableModel.rowAt(table.convertRowIndexToModel(selectedRow)).characterId();
        new EveSettingsCopyDialog(this, appContext, preferredSourceId).setVisible(true);
    }

    private void updateMainButton() {
        int selectedRow = table.getSelectedRow();
        boolean selectable = selectedRow >= 0;
        if (selectable) {
            EveCharacter selected = tableModel.rowAt(table.convertRowIndexToModel(selectedRow));
            selectable = tableModel.mainCharacterId == null || tableModel.mainCharacterId != selected.characterId();
        }
        mainButton.setEnabled(selectable);
    }

    private void reloadCharacters() {
        List<EveCharacter> characters = appContext.characterService.listCharactersMainFirst();
        tableModel.setRows(characters, appContext.characterService.mainCharacterId().orElse(null),
                corporationAccess(characters));
        TableStyler.packColumns(table);
        updateMainButton();
    }

    private Map<Long, String> corporationAccess(List<EveCharacter> characters) {
        Map<Long, CorporationAccess> access = appContext.corporationService.accessByCharacter();
        Map<Long, Corporation> corporations = appContext.corporationService.corporations();
        Map<Long, String> texts = new HashMap<>();
        for (EveCharacter character : characters) {
            CorporationAccess entry = access.get(character.characterId());
            texts.put(character.characterId(), corporationText(CorporationService.reads(character), entry,
                    entry == null ? null : corporations.get(entry.corporationId())));
        }
        return texts;
    }

    static String corporationText(boolean asked, CorporationAccess access, Corporation corporation) {
        if (!asked) {
            return "Not asked";
        }
        if (access == null) {
            return "Run Update > Corporation";
        }
        return (corporation == null ? "Corporation #" + access.corporationId() : corporation.label()) + "  ·  "
                + access.summary();
    }

    private static final class CharacterTableModel extends AbstractTableModel implements ColumnDescriptions {
        private final String[] columns = {"Character", "Character ID", "Added", "Corporation"};
        private final String[] descriptions = {"The character's name", "EVE's ID of the character",
                "When you added the character to EVE Farm, in your local time",
                "The character's corporation and the roles EVE Farm can use to read it. Not asked means the "
                        + "character was added without Also read the corporation"};
        private List<EveCharacter> rows = new ArrayList<>();
        private Long mainCharacterId;
        private Map<Long, String> corporations = Map.of();

        void setRows(List<EveCharacter> rows, Long mainCharacterId, Map<Long, String> corporations) {
            this.rows = rows;
            this.mainCharacterId = mainCharacterId;
            this.corporations = corporations;
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
        public String columnDescription(int column) {
            return descriptions[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            EveCharacter character = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> character.characterName();
                case 1 -> character.characterId();
                case 2 -> DateUtil.format(character.addedAt());
                case 3 -> corporations.getOrDefault(character.characterId(), "");
                default -> "";
            };
        }
    }
}
