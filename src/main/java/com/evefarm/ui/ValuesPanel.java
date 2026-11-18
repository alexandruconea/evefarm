package com.evefarm.ui;

import com.evefarm.AppContext;
import com.evefarm.model.EveCharacter;
import com.evefarm.model.ValueSummary;
import com.evefarm.service.TrackerCombination;
import com.evefarm.service.TrackerCombinationService;

import javax.swing.BorderFactory;
import javax.swing.GroupLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.LayoutStyle;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public final class ValuesPanel extends TabPanel {

    private final AppContext appContext;
    private final ValueColumnPanel grandTotalColumn = new ValueColumnPanel();
    private final ValueColumnPanel characterColumn = new ValueColumnPanel();
    private final ForecastPanel projectionPanel = new ForecastPanel(this::chooseCombination, this::editCombinations);
    private final AtomicInteger projectionGeneration = new AtomicInteger();

    public ValuesPanel(AppContext appContext) {
        this.appContext = appContext;
        initComponents();
        postInit();
    }

    private void postInit() {
        columnsContainer.setLayout(new GridLayout(1, 2, 20, 0));
        columnsContainer.setOpaque(false);
        columnsScrollPane.setBorder(BorderFactory.createEmptyBorder());
        columnsContainer.add(grandTotalColumn);
        columnsContainer.add(characterColumn);
        FillViewportPanel content = new FillViewportPanel();
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(columnsContainer, BorderLayout.NORTH);
        content.add(projectionPanel, BorderLayout.CENTER);
        columnsScrollPane.setViewportView(content);

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

        refreshGrandTotalColumn();
        refreshCharacterColumn();
        refreshProjection();
    }

    private void refreshProjection() {
        List<EveCharacter> characters = characterCombo.characters();
        projectionPanel.setVisible(!characters.isEmpty());
        if (characters.isEmpty()) {
            return;
        }
        EveCharacter main = characters.getFirst();
        TrackerCombinationService combinations = appContext.trackerCombinationService;
        TrackerCombination combination = combinations.forecastCombination();
        projectionPanel.setCombinations(combinations.combinations().stream().map(TrackerCombination::name).toList(),
                combination.name());
        BackgroundLoader.load(projectionGeneration, () -> combinations.project(combination,
                        Set.of(main.characterId())),
                projection -> projectionPanel.setProjection(main.characterName(), combination, projection),
                "the forecast");
    }

    private void chooseCombination(String name) {
        appContext.trackerCombinationService.select(name);
        refreshProjection();
    }

    private void editCombinations() {
        new TrackerCombinationsDialog(SwingUtilities.getWindowAncestor(this), appContext.trackerCombinationService,
                projectionPanel.combinationName(), this::chooseCombination).setVisible(true);
    }

    private void refreshGrandTotalColumn() {
        ValueSummary summary = appContext.valueSummaryService.summarizeGrandTotal();
        grandTotalColumn.setSummary("Grand Total", summary);
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
            return getParent() instanceof JViewport viewport && viewport.getWidth() > getPreferredSize().width;
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

        GroupLayout columnsContainerLayout = new GroupLayout(columnsContainer);
        columnsContainer.setLayout(columnsContainerLayout);
        columnsContainerLayout.setHorizontalGroup(
            columnsContainerLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGap(0, 860, Short.MAX_VALUE)
        );
        columnsContainerLayout.setVerticalGroup(
            columnsContainerLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGap(0, 460, Short.MAX_VALUE)
        );

        columnsScrollPane.setViewportView(columnsContainer);

        GroupLayout layout = new GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                    .addComponent(columnsScrollPane, GroupLayout.DEFAULT_SIZE, 880, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(characterLabel)
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(characterCombo, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(characterLabel)
                    .addComponent(characterCombo, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(columnsScrollPane, GroupLayout.DEFAULT_SIZE, 480, Short.MAX_VALUE)
                .addContainerGap())
        );
    }
}
