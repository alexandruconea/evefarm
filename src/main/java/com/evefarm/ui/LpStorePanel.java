package com.evefarm.ui;

import com.evefarm.AppContext;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.model.EveCharacter;
import com.evefarm.model.LoyaltyPointRow;
import com.evefarm.model.LpOfferRow;
import com.evefarm.model.LpRequiredItem;
import com.evefarm.model.NpcCorporationRow;
import com.evefarm.service.ItemIconService;
import com.evefarm.ui.column.ColumnCopySupport;
import com.evefarm.ui.column.ColumnSorting;
import com.evefarm.ui.column.ColumnVisibilitySupport;
import com.evefarm.ui.filter.FilterBarPanel;
import com.evefarm.util.IskFormatter;
import com.formdev.flatlaf.FlatLaf;

import javax.swing.DefaultComboBoxModel;
import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.LayoutStyle;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class LpStorePanel extends JPanel {

    private static final String PANEL_KEY = "lpStore";

    private final AppContext appContext;
    private final LpStoreTableModel tableModel;
    private FilterBarPanel filterBarPanel;
    private TableRowSorter<LpStoreTableModel> sorter;

    private List<EveCharacter> characters = List.of();
    private List<NpcCorporationRow> allCorporations = List.of();
    private long selectedCorporationId = -1;
    private int offerCount = 0;
    private double targetIskPerLp;
    private long yourLp = 0;
    private String mainCharacterName;

    public LpStorePanel(AppContext appContext) {
        initComponents();
        this.appContext = appContext;
        this.tableModel = new LpStoreTableModel(appContext.itemIconService);
        postInit();
    }

    private void postInit() {
        table.setModel(tableModel);
        sorter = new TableRowSorter<>(tableModel);
        ColumnSorting.install(sorter, tableModel);
        table.setRowSorter(sorter);
        TableStyler.style(table);
        table.setRowHeight(Math.max(table.getRowHeight(), ItemIconService.RENDER_SIZE + 6));
        installIskPerLpRenderer();
        ColumnVisibilitySupport.install(table, tableModel, PANEL_KEY, appContext.tableColumnStateDao);

        filterBarPanel = new FilterBarPanel(PANEL_KEY, tableModel.columnNames(), appContext.savedFilterDao);
        filterBarPanel.setOnFilterChanged(this::applyFilter);
        filterBarContainer.setLayout(new BorderLayout());
        filterBarContainer.add(filterBarPanel, BorderLayout.CENTER);

        setTargetButton.setIcon(Icons.TARGET);
        yourLpLabel.setFont(yourLpLabel.getFont().deriveFont(Font.BOLD));
        ColumnCopySupport.install(table, tableModel, this::copyLpColumn);

        characterCombo.addActionListener(e -> updateYourLpLabel());
        characterCombo.setRenderer(MainCharacterMarks.comboRenderer(() -> mainCharacterName));
        corporationCombo.addActionListener(e -> onCorporationSelected());
        favoriteCheckBox.addActionListener(e -> onFavoriteToggled());
        refreshOffersButton.addActionListener(e -> fetchOffers());
        setTargetButton.addActionListener(e -> openTargetDialog());
        historyButton.setIcon(Icons.CHART);
        historyButton.addActionListener(e -> LoyaltyPointHistoryDialog.open(
                SwingUtilities.getWindowAncestor(this), appContext));

        loadTargetIskPerLp();
        reloadCharacters(true);
        loadAllCorporations();
    }

    private void loadTargetIskPerLp() {
        targetIskPerLp = appContext.settingsDao.getDouble(SettingsDao.LP_STORE_TARGET_ISK_PER_LP, 500);
    }

    private void openTargetDialog() {
        LpTargetDialog dialog = new LpTargetDialog(
                SwingUtilities.getWindowAncestor(this), appContext.settingsDao, () -> {
                    loadTargetIskPerLp();
                    updateWealthLabel();
                    table.repaint();
                });
        dialog.setVisible(true);
    }

    private void installIskPerLpRenderer() {
        TableCellRenderer defaultRenderer = table.getDefaultRenderer(Object.class);
        table.setDefaultRenderer(Object.class, (tbl, value, isSelected, hasFocus, row, column) -> {
            Component c = defaultRenderer.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, column);
            if (!isSelected && targetIskPerLp > 0) {
                int modelColumn = tbl.convertColumnIndexToModel(column);
                String key = tableModel.columns().get(modelColumn).key();
                if ("iskPerLpSell".equals(key) || "iskPerLpBuy".equals(key)) {
                    LpOfferRow offerRow = tableModel.rowAt(tbl.convertRowIndexToModel(row));
                    Double raw = "iskPerLpSell".equals(key) ? offerRow.iskPerLpSell() : offerRow.iskPerLpBuy();
                    Color background = colorFor(raw);
                    if (background != null) {
                        c.setBackground(background);
                    }
                }
            }
            return c;
        });
    }

    private Color colorFor(Double iskPerLp) {
        if (iskPerLp == null) {
            return null;
        }
        boolean dark = FlatLaf.isLafDark();
        if (iskPerLp >= targetIskPerLp) {
            return dark ? new Color(27, 94, 32) : new Color(200, 230, 201);
        }
        return dark ? new Color(127, 29, 29) : new Color(255, 205, 210);
    }

    public void onShown() {
        reloadCharacters(false);
    }

    public void refreshCharacterFilter() {
        reloadCharacters(true);
    }

    private void reloadCharacters(boolean selectMain) {
        characters = appContext.characterService.listCharactersMainFirst();
        Long mainId = appContext.characterService.mainCharacterId().orElse(null);
        mainCharacterName = characters.stream()
                .filter(character -> mainId != null && character.characterId() == mainId)
                .map(EveCharacter::characterName)
                .findFirst().orElse(null);
        String previouslySelected = (String) characterCombo.getSelectedItem();

        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        for (EveCharacter character : characters) {
            model.addElement(character.characterName());
        }
        characterCombo.setModel(model);
        if (!selectMain && previouslySelected != null && model.getIndexOf(previouslySelected) >= 0) {
            characterCombo.setSelectedItem(previouslySelected);
        } else if (model.getSize() > 0) {
            characterCombo.setSelectedIndex(0);
        }
        updateYourLpLabel();
    }

    private void loadAllCorporations() {
        corporationCombo.setEnabled(false);
        new SwingWorker<List<NpcCorporationRow>, Void>() {
            @Override
            protected List<NpcCorporationRow> doInBackground() {
                return appContext.loyaltyPointService.listAllNpcCorporations();
            }

            @Override
            protected void done() {
                corporationCombo.setEnabled(true);
                try {
                    allCorporations = get();
                } catch (Exception e) {
                    allCorporations = List.of();
                }
                DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
                for (NpcCorporationRow corp : allCorporations) {
                    model.addElement(corp.corporationName());
                }
                corporationCombo.setModel(model);
                if (!allCorporations.isEmpty()) {
                    corporationCombo.setSelectedIndex(indexOfFavoriteCorporation());
                }
            }
        }.execute();
    }

    private int indexOfFavoriteCorporation() {
        String favoriteId = appContext.settingsDao.getOrDefault(SettingsDao.LP_STORE_FAVORITE_CORPORATION_ID, "");
        if (favoriteId.isBlank()) {
            return 0;
        }
        for (int i = 0; i < allCorporations.size(); i++) {
            if (favoriteId.equals(String.valueOf(allCorporations.get(i).corporationId()))) {
                return i;
            }
        }
        return 0;
    }

    private void onCorporationSelected() {
        int index = corporationCombo.getSelectedIndex();
        long newCorporationId = (index >= 0 && index < allCorporations.size())
                ? allCorporations.get(index).corporationId() : -1;
        updateYourLpLabel();
        if (newCorporationId == selectedCorporationId) {
            return;
        }
        selectedCorporationId = newCorporationId;
        updateFavoriteCheckBox();
        tableModel.setRows(List.of());
        offerCount = 0;
        updateOfferCountLabel();
        applyFilter();
    }

    private void updateFavoriteCheckBox() {
        String favoriteId = appContext.settingsDao.getOrDefault(SettingsDao.LP_STORE_FAVORITE_CORPORATION_ID, "");
        favoriteCheckBox.setSelected(!favoriteId.isBlank() && favoriteId.equals(String.valueOf(selectedCorporationId)));
    }

    private void onFavoriteToggled() {
        if (selectedCorporationId < 0) {
            favoriteCheckBox.setSelected(false);
            return;
        }
        appContext.settingsDao.set(SettingsDao.LP_STORE_FAVORITE_CORPORATION_ID,
                favoriteCheckBox.isSelected() ? String.valueOf(selectedCorporationId) : "");
    }

    private void fetchOffers() {
        if (selectedCorporationId < 0) {
            tableModel.setRows(List.of());
            offerCount = 0;
            updateOfferCountLabel();
            applyFilter();
            return;
        }
        long corporationId = selectedCorporationId;
        refreshOffersButton.setEnabled(false);
        new SwingWorker<List<LpOfferRow>, Void>() {
            @Override
            protected List<LpOfferRow> doInBackground() {
                return appContext.lpOfferPricingService.listPricedOffers(corporationId);
            }

            @Override
            protected void done() {
                refreshOffersButton.setEnabled(true);
                try {
                    List<LpOfferRow> rows = get();
                    tableModel.setRows(rows);
                    offerCount = rows.size();
                    TableStyler.packColumns(table);
                    applyFilter();
                    loadIcons(rows);
                } catch (Exception ignored) {
                    offerCount = -1;
                }
                updateOfferCountLabel();
            }
        }.execute();
    }

    private void loadIcons(List<LpOfferRow> rows) {
        rows.stream().map(LpOfferRow::typeId).distinct()
                .forEach(typeId -> appContext.itemIconService.loadAsync(typeId, table::repaint));
    }

    private void updateOfferCountLabel() {
        offerCountLabel.setText(offerCount < 0 ? "Failed to load offers" : offerCount + " offers");
    }

    private void updateYourLpLabel() {
        String selectedCharacterName = (String) characterCombo.getSelectedItem();
        EveCharacter character = characters.stream()
                .filter(c -> c.characterName().equals(selectedCharacterName))
                .findFirst().orElse(null);
        if (character == null || selectedCorporationId < 0) {
            yourLp = 0;
            yourLpLabel.setText("Your LP: -");
            updateWealthLabel();
            return;
        }
        List<LoyaltyPointRow> balances = appContext.loyaltyPointService.getLoyaltyPoints(character.characterId());
        yourLp = balances.stream()
                .filter(b -> b.corporationId() == selectedCorporationId)
                .mapToLong(LoyaltyPointRow::loyaltyPoints)
                .findFirst().orElse(0);
        yourLpLabel.setText("Your LP: " + String.format("%,d", yourLp));
        updateWealthLabel();
    }

    private void updateWealthLabel() {
        if (yourLp <= 0 || targetIskPerLp <= 0) {
            wealthLabel.setText("Estimated Value: -");
            return;
        }
        wealthLabel.setText("Estimated Value: " + IskFormatter.format(yourLp * targetIskPerLp));
    }

    private String copyLpColumn(String columnKey, List<LpOfferRow> selectedRows) {
        if ("otherRequirements".equals(columnKey)) {
            return formatAsMultibuy(selectedRows, LpOfferRow::requiredItems);
        }
        if ("buildMaterials".equals(columnKey)) {
            return formatAsMultibuy(selectedRows, LpOfferRow::buildMaterials);
        }
        return null;
    }

    private String formatAsMultibuy(List<LpOfferRow> selectedRows, Function<LpOfferRow, List<LpRequiredItem>> items) {
        Map<String, Long> merged = new LinkedHashMap<>();
        for (LpOfferRow row : selectedRows) {
            for (LpRequiredItem item : items.apply(row)) {
                merged.merge(item.itemName(), item.quantity(), Long::sum);
            }
        }
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, Long> entry : merged.entrySet()) {
            text.append(entry.getKey()).append('\t').append(entry.getValue()).append('\n');
        }
        return text.toString();
    }

    private void applyFilter() {
        sorter.setRowFilter(filterBarPanel.buildRowFilter());
        filterBarPanel.setRowCounts(table.getRowCount(), tableModel.getRowCount());
    }

    private JComboBox<String> characterCombo;
    private JLabel characterLabel;
    private JComboBox<String> corporationCombo;
    private JLabel corporationLabel;
    private JCheckBox favoriteCheckBox;
    private JPanel filterBarContainer;
    private JScrollPane jScrollPane2;
    private JLabel offerCountLabel;
    private JButton refreshOffersButton;
    private JButton setTargetButton;
    private JButton historyButton;
    private JTable table;
    private JLabel wealthLabel;
    private JLabel yourLpLabel;

    private void initComponents() {

        characterLabel = new JLabel();
        characterCombo = new JComboBox<>();
        corporationLabel = new JLabel();
        corporationCombo = new JComboBox<>();
        favoriteCheckBox = new JCheckBox();
        yourLpLabel = new JLabel();
        wealthLabel = new JLabel();
        refreshOffersButton = new JButton();
        setTargetButton = new JButton();
        historyButton = new JButton("LP History...");
        filterBarContainer = new JPanel();
        jScrollPane2 = new JScrollPane();
        table = new JTable();
        offerCountLabel = new JLabel();

        characterLabel.setText("Character:");

        corporationLabel.setText("Corporation:");

        favoriteCheckBox.setText("Favorite");

        yourLpLabel.setText("Your LP: -");

        wealthLabel.setText("Estimated Value: -");

        refreshOffersButton.setText("Show Offers");

        setTargetButton.setText("Set ISK/LP Target...");

        GroupLayout filterBarContainerLayout = new GroupLayout(filterBarContainer);
        filterBarContainer.setLayout(filterBarContainerLayout);
        filterBarContainerLayout.setHorizontalGroup(
            filterBarContainerLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );
        filterBarContainerLayout.setVerticalGroup(
            filterBarContainerLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGap(0, 60, Short.MAX_VALUE)
        );

        jScrollPane2.setViewportView(table);

        offerCountLabel.setText("0 offers");

        GroupLayout layout = new GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                    .addComponent(filterBarContainer, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jScrollPane2)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(offerCountLabel)
                                .addGap(18, 18, 18)
                                .addComponent(yourLpLabel)
                                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(wealthLabel))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(characterLabel)
                                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(characterCombo, GroupLayout.PREFERRED_SIZE, 160, GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(corporationLabel)
                                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(corporationCombo, GroupLayout.PREFERRED_SIZE, 260, GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(favoriteCheckBox)
                                .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(refreshOffersButton)
                                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(setTargetButton)
                                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(historyButton)))
                        .addGap(0, 167, Short.MAX_VALUE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(characterLabel)
                    .addComponent(characterCombo, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                    .addComponent(corporationLabel)
                    .addComponent(corporationCombo, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                    .addComponent(favoriteCheckBox)
                    .addComponent(refreshOffersButton)
                    .addComponent(setTargetButton)
                    .addComponent(historyButton))
                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(filterBarContainer, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane2, GroupLayout.DEFAULT_SIZE, 365, Short.MAX_VALUE)
                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(offerCountLabel)
                    .addComponent(yourLpLabel)
                    .addComponent(wealthLabel))
                .addContainerGap())
        );
    }
}
