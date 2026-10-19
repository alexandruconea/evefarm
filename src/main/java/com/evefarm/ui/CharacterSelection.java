package com.evefarm.ui;

import com.evefarm.model.EveCharacter;
import com.evefarm.service.CharacterService;

import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListSelectionModel;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

final class CharacterSelection {

    private final CharacterService characterService;
    private final JCheckBox allProfilesCheckBox;
    private final JList<EveCharacter> characterList;
    private final DefaultListModel<EveCharacter> characterListModel = new DefaultListModel<>();
    private Long mainCharacterId;

    CharacterSelection(CharacterService characterService, JCheckBox allProfilesCheckBox,
                       JList<EveCharacter> characterList, Runnable onChange) {
        this.characterService = characterService;
        this.allProfilesCheckBox = allProfilesCheckBox;
        this.characterList = characterList;
        allProfilesCheckBox.addActionListener(e -> {
            characterList.setEnabled(!allProfilesCheckBox.isSelected());
            onChange.run();
        });
        characterList.setModel(characterListModel);
        characterList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        characterList.setEnabled(false);
        characterList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel(value.characterName());
            label.setIcon(MainCharacterMarks.iconFor(mainCharacterId, value.characterId()));
            label.setOpaque(true);
            label.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
            label.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
            return label;
        });
        characterList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onChange.run();
            }
        });
    }

    void reload() {
        characterListModel.clear();
        mainCharacterId = characterService.mainCharacterId().orElse(null);
        for (EveCharacter character : characterService.listCharactersMainFirst()) {
            characterListModel.addElement(character);
        }
        if (!characterListModel.isEmpty()) {
            characterList.setSelectionInterval(0, characterListModel.size() - 1);
        }
    }

    Set<Long> selectedIds() {
        List<EveCharacter> characters = allProfilesCheckBox.isSelected() ? characterService.listCharacters()
                : characterList.getSelectedValuesList();
        return characters.stream()
                .map(EveCharacter::characterId)
                .collect(Collectors.toSet());
    }

    String label() {
        if (allProfilesCheckBox.isSelected()) {
            return "All Profiles";
        }
        List<String> names = characterList.getSelectedValuesList().stream()
                .map(EveCharacter::characterName)
                .toList();
        return names.isEmpty() ? "No characters selected" : String.join(", ", names);
    }
}
