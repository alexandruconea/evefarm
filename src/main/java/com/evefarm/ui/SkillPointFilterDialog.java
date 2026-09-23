package com.evefarm.ui;

import com.evefarm.AppContext;
import com.evefarm.model.EveCharacter;
import com.evefarm.model.SkillPointFilter;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SkillPointFilterDialog extends JDialog {

    private final AppContext appContext;
    private final Map<Long, JCheckBox> enabledBoxes = new LinkedHashMap<>();
    private final Map<Long, JSpinner> minimumSpinners = new LinkedHashMap<>();

    public SkillPointFilterDialog(Window owner, AppContext appContext) {
        super(owner, "Skill Point Filters", ModalityType.APPLICATION_MODAL);
        this.appContext = appContext;
        buildUi();
        pack();
        setLocationRelativeTo(owner);
    }

    private void buildUi() {
        List<EveCharacter> characters = appContext.characterService.listCharacters();

        JPanel rows = new JPanel();
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        rows.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel header = new JPanel(new GridLayout(1, 3));
        header.add(new JLabel("Character"));
        header.add(new JLabel("Tracked"));
        header.add(new JLabel("Extra SP kept (above 5,000,000)"));
        rows.add(header);

        for (EveCharacter character : characters) {
            SkillPointFilter filter = appContext.skillPointFilterDao.find(character.characterId());

            JPanel row = new JPanel(new GridLayout(1, 3));
            row.add(new JLabel(character.characterName()));

            JCheckBox enabledBox = new JCheckBox("", filter.enabled());
            row.add(enabledBox);

            JSpinner minimumSpinner = new JSpinner(
                    new SpinnerNumberModel((long) filter.minimumSp(), 0L, 2_000_000_000L, 500_000L));
            row.add(minimumSpinner);

            enabledBoxes.put(character.characterId(), enabledBox);
            minimumSpinners.put(character.characterId(), minimumSpinner);
            rows.add(row);
        }

        JScrollPane scrollPane = new JScrollPane(rows);
        scrollPane.setPreferredSize(new Dimension(480, 220));

        JButton saveButton = new JButton("Save", Icons.SAVE);
        saveButton.addActionListener(this::onSave);
        JButton cancelButton = new JButton("Cancel", Icons.CANCEL);
        cancelButton.addActionListener(e -> dispose());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(ButtonSizing.row(6, saveButton, cancelButton));

        setLayout(new BorderLayout());
        add(scrollPane, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void onSave(ActionEvent e) {
        for (Map.Entry<Long, JCheckBox> entry : enabledBoxes.entrySet()) {
            long characterId = entry.getKey();
            boolean enabled = entry.getValue().isSelected();
            long minimumSp = ((Number) minimumSpinners.get(characterId).getValue()).longValue();
            appContext.skillPointFilterDao.upsert(characterId, enabled, minimumSp);
        }
        dispose();
    }
}
