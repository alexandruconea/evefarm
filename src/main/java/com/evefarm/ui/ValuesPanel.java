package com.evefarm.ui;

import com.evefarm.AppContext;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.model.EveCharacter;
import com.evefarm.model.ValueSummary;
import com.evefarm.service.CorporationService;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.List;

public final class ValuesPanel extends TabPanel {

    private final AppContext appContext;
    private final ValueColumnPanel grandTotalColumn = new ValueColumnPanel();
    private final ValueColumnPanel characterColumn = new ValueColumnPanel();
    private final ValueColumnPanel corporationColumn = new ValueColumnPanel();
    private final JComboBox<String> corporationCombo = new JComboBox<>();
    private final JCheckBox corporationsInTotalBox = new JCheckBox("Add the corporations to the Grand Total");
    private final JPanel corporationControls = new JPanel();
    private List<CorporationService.Card> cards = List.of();
    private boolean fillingCorporations;

    public ValuesPanel(AppContext appContext) {
        this.appContext = appContext;
        initComponents();
        postInit();
    }

    private void postInit() {
        setLayout(new BorderLayout());
        columnsContainer.setOpaque(false);
        columnsContainer.setLayout(new ResponsiveGrid(20));
        columnsScrollPane.setBorder(BorderFactory.createEmptyBorder());
        FillViewportPanel content = new FillViewportPanel();
        content.setBorder(BorderFactory.createEmptyBorder(2, 12, 12, 12));
        content.add(columnsContainer, BorderLayout.NORTH);
        columnsScrollPane.setViewportView(content);

        ComboSizing.names(corporationCombo);
        ComboFilter.install(corporationCombo);
        corporationCombo.setToolTipText("The corporation shown in the third card");
        corporationCombo.addActionListener(e -> {
            if (!fillingCorporations) {
                refreshCorporationColumn();
            }
        });
        corporationsInTotalBox.setToolTipText("Count the wallets, assets and sell orders of your corporations in "
                + "the Grand Total too. They belong to the corporation, not to you, so they are left out at first");
        corporationsInTotalBox.setSelected("true".equals(appContext.settingsDao.getOrDefault(
                SettingsDao.CORPORATIONS_IN_GRAND_TOTAL, "false")));
        corporationsInTotalBox.addActionListener(e -> {
            appContext.settingsDao.set(SettingsDao.CORPORATIONS_IN_GRAND_TOTAL,
                    String.valueOf(corporationsInTotalBox.isSelected()));
            refreshGrandTotalColumn();
        });
        corporationControls.setOpaque(false);
        corporationControls.setLayout(new BoxLayout(corporationControls, BoxLayout.X_AXIS));
        corporationControls.add(new JLabel("Corporation:"));
        corporationControls.add(Box.createHorizontalStrut(6));
        corporationControls.add(corporationCombo);
        corporationControls.add(Box.createHorizontalStrut(16));
        corporationControls.add(corporationsInTotalBox);
        JPanel controls = new JPanel(new WrapFlowLayout(6, 6));
        controls.setBorder(BorderFactory.createEmptyBorder(4, 6, 0, 10));
        controls.add(characterLabel);
        controls.add(characterCombo);
        controls.add(Box.createHorizontalStrut(20));
        controls.add(corporationControls);
        add(controls, BorderLayout.NORTH);
        add(columnsScrollPane, BorderLayout.CENTER);

        characterCombo.addActionListener(e -> refreshCharacterColumn());

        reloadCharacters(true);
    }

    @Override
    public void onShown() {
        reloadCharacters(false);
    }

    @Override
    public void refreshCharacterFilter() {
        reloadCharacters(true);
    }

    private void reloadCharacters(boolean selectMain) {
        characterCombo.reload(selectMain);

        refreshCorporations();
        refreshGrandTotalColumn();
        refreshCharacterColumn();
    }

    private void refreshCorporations() {
        cards = appContext.corporationService.cards();
        Object previous = corporationCombo.getSelectedItem();
        fillingCorporations = true;
        try {
            corporationCombo.removeAllItems();
            cards.forEach(card -> corporationCombo.addItem(card.corporation().label()));
            if (previous != null) {
                corporationCombo.setSelectedItem(previous);
            }
        } finally {
            fillingCorporations = false;
        }
        boolean any = !cards.isEmpty();
        corporationControls.setVisible(any);
        columnsContainer.removeAll();
        columnsContainer.add(grandTotalColumn);
        columnsContainer.add(characterColumn);
        if (any) {
            columnsContainer.add(corporationColumn);
        }
        columnsContainer.revalidate();
        columnsContainer.repaint();
        refreshCorporationColumn();
    }

    private void refreshCorporationColumn() {
        Object selected = corporationCombo.getSelectedItem();
        cards.stream().filter(card -> card.corporation().label().equals(selected)).findFirst()
                .ifPresent(card -> corporationColumn.setSummary(card.corporation().label(), card.summary()));
    }

    private void refreshGrandTotalColumn() {
        ValueSummary summary = appContext.valueSummaryService.summarizeGrandTotal();
        grandTotalColumn.setSummary("Grand Total", corporationsInTotalBox.isSelected()
                ? withCorporations(summary, cards) : summary);
    }

    static ValueSummary withCorporations(ValueSummary personal, List<CorporationService.Card> cards) {
        double wallets = cards.stream().mapToDouble(card -> card.summary().walletBalance()).sum();
        double assets = cards.stream().mapToDouble(card -> card.summary().assetsValue()).sum();
        double sellOrders = cards.stream().mapToDouble(card -> card.summary().sellOrdersValue()).sum();
        return new ValueSummary(personal.total() + wallets + assets + sellOrders, personal.walletBalance() + wallets,
                personal.assetsValue() + assets, personal.sellOrdersValue() + sellOrders, personal.today(),
                personal.bestAssetName(), personal.bestAssetValue(), personal.bestShipName(), personal.bestShipValue(),
                personal.bestModuleName(), personal.bestModuleValue());
    }

    private void refreshCharacterColumn() {
        EveCharacter selected = characterCombo.selectedCharacter();
        if (selected == null) {
            characterColumn.setSummary("Character", null);
            return;
        }
        ValueSummary summary = appContext.valueSummaryService.summarizeCharacter(selected.characterId());
        characterColumn.setSummary(selected.characterName(), summary);
    }

    private static final class FillViewportPanel extends JPanel implements Scrollable {

        FillViewportPanel() {
            super(new BorderLayout(0, 20));
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(16, visibleRect.height - 16);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return getParent() instanceof JViewport viewport && viewport.getWidth() >= getMinimumSize().width;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return getParent() instanceof JViewport viewport && viewport.getHeight() > getPreferredSize().height;
        }
    }

    private JLabel characterLabel;
    private CharacterCombo characterCombo;
    private JPanel columnsContainer;
    private JScrollPane columnsScrollPane;

    private void initComponents() {

        characterLabel = new JLabel();
        characterCombo = new CharacterCombo(appContext.characterService);
        columnsScrollPane = new JScrollPane();
        columnsContainer = new JPanel();

        characterLabel.setText("Character:");
    }
}
