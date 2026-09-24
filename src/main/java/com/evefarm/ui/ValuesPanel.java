package com.evefarm.ui;

import com.evefarm.AppContext;
import com.evefarm.model.EveCharacter;
import com.evefarm.model.ValueSummary;

import java.awt.GridLayout;
import java.util.List;

public final class ValuesPanel extends javax.swing.JPanel {

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
        columnsContainer.setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 12, 12, 12));
        columnsScrollPane.setBorder(javax.swing.BorderFactory.createEmptyBorder());
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

        javax.swing.DefaultComboBoxModel<String> model = new javax.swing.DefaultComboBoxModel<>();
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

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel characterLabel;
    private javax.swing.JComboBox<String> characterCombo;
    private javax.swing.JPanel columnsContainer;
    private javax.swing.JScrollPane columnsScrollPane;
    // End of variables declaration//GEN-END:variables

    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        characterLabel = new javax.swing.JLabel();
        characterCombo = new javax.swing.JComboBox<>();
        columnsScrollPane = new javax.swing.JScrollPane();
        columnsContainer = new javax.swing.JPanel();

        characterLabel.setText("Character:");

        javax.swing.GroupLayout columnsContainerLayout = new javax.swing.GroupLayout(columnsContainer);
        columnsContainer.setLayout(columnsContainerLayout);
        columnsContainerLayout.setHorizontalGroup(
            columnsContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 860, Short.MAX_VALUE)
        );
        columnsContainerLayout.setVerticalGroup(
            columnsContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 460, Short.MAX_VALUE)
        );

        columnsScrollPane.setViewportView(columnsContainer);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(columnsScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 880, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(characterLabel)
                        .addGap(18, 18, 18)
                        .addComponent(characterCombo, javax.swing.GroupLayout.PREFERRED_SIZE, 220, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(characterLabel)
                    .addComponent(characterCombo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(columnsScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 480, Short.MAX_VALUE)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents
}
