package com.evefarm.ui;

import com.evefarm.AppContext;
import com.evefarm.model.EveCharacter;
import com.evefarm.model.ValueSummary;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.GroupLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.LayoutStyle;
import java.awt.GridLayout;
import java.util.List;

public final class ValuesPanel extends JPanel {

    private final AppContext appContext;
    private final ValueColumnPanel grandTotalColumn = new ValueColumnPanel();
    private final ValueColumnPanel characterColumn = new ValueColumnPanel();
    private List<EveCharacter> characters = List.of();
    private String mainCharacterName;

    public ValuesPanel(AppContext appContext) {
        initComponents();
        this.appContext = appContext;
        postInit();
    }

    private void postInit() {
        columnsContainer.setLayout(new GridLayout(1, 2, 20, 0));
        columnsContainer.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        columnsScrollPane.setBorder(BorderFactory.createEmptyBorder());
        columnsContainer.add(grandTotalColumn);
        columnsContainer.add(characterColumn);

        characterCombo.addActionListener(e -> refreshCharacterColumn());
        characterCombo.setRenderer(MainCharacterMarks.comboRenderer(() -> mainCharacterName));

        reloadCharacters(true);
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

        refreshGrandTotalColumn();
        refreshCharacterColumn();
    }

    private void refreshGrandTotalColumn() {
        ValueSummary summary = appContext.valueSummaryService.summarizeGrandTotal();
        grandTotalColumn.setSummary("Grand Total", summary);
    }

    private void refreshCharacterColumn() {
        String selectedName = (String) characterCombo.getSelectedItem();
        EveCharacter selected = characters.stream()
                .filter(c -> c.characterName().equals(selectedName))
                .findFirst().orElse(null);
        if (selected == null) {
            characterColumn.setSummary("Character", null);
            return;
        }
        ValueSummary summary = appContext.valueSummaryService.summarizeCharacter(selected.characterId());
        characterColumn.setSummary(selected.characterName(), summary);
    }

    private JLabel characterLabel;
    private JComboBox<String> characterCombo;
    private JPanel columnsContainer;
    private JScrollPane columnsScrollPane;

    private void initComponents() {

        characterLabel = new JLabel();
        characterCombo = new JComboBox<>();
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
                        .addGap(18, 18, 18)
                        .addComponent(characterCombo, GroupLayout.PREFERRED_SIZE, 220, GroupLayout.PREFERRED_SIZE)))
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
