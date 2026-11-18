package com.evefarm.ui;

import com.evefarm.service.TrackerCombination;
import com.evefarm.service.TrackerCombinationService;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

final class TrackerCombinationsDialog extends JDialog {

    private static final String TITLE = "Tracker Combinations";
    private static final Map<String, String> PART_TIPS = Map.ofEntries(
            Map.entry("Wallet Balance", "The ISK in the wallets"),
            Map.entry("Assets", "What everything the characters own is worth"),
            Map.entry("Implants", "The implants in every clone"),
            Map.entry("Sell Orders", "The items on open sell orders"),
            Map.entry("Escrows", "The ISK held for open buy orders"),
            Map.entry("Manufacturing", "What the running manufacturing and reaction jobs will make"),
            Map.entry("Contract Collateral", "The collateral of open courier contracts"),
            Map.entry("Contracts", "The price and reward of the open contracts you issued"),
            Map.entry("Skill Points", "The skill points as counted by Skill Point Filters..."),
            Map.entry("LP Value", "The LP with your favorite corporation, at your ISK/LP target"),
            Map.entry(TrackerCombination.MINING, "The ISK value of everything mined so far, valued as chosen in "
                    + "Industry > Mining. Ore you sold is in Wallet Balance too, and ore you kept in Assets."));

    private final TrackerCombinationService service;
    private final Consumer<String> onSaved;
    private final DefaultListModel<TrackerCombination> combinations = new DefaultListModel<>();
    private final JList<TrackerCombination> list = new JList<>(combinations);
    private final Map<String, JCheckBox> partBoxes = new LinkedHashMap<>();
    private final JButton renameButton = new JButton("Rename...");
    private final JButton deleteButton = new JButton("Delete", Icons.REMOVE);
    private boolean showing;

    TrackerCombinationsDialog(Window owner, TrackerCombinationService service, String selectedName,
                              Consumer<String> onSaved) {
        super(owner, TITLE, ModalityType.APPLICATION_MODAL);
        this.service = service;
        this.onSaved = onSaved;
        service.combinations().forEach(combinations::addElement);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer((jList, combination, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel(combination.name());
            label.setOpaque(true);
            label.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            label.setBackground(isSelected ? jList.getSelectionBackground() : jList.getBackground());
            label.setForeground(isSelected ? jList.getSelectionForeground() : jList.getForeground());
            return label;
        });
        list.addListSelectionListener(e -> showParts());

        JButton newButton = new JButton("New...", Icons.ADD);
        newButton.addActionListener(e -> addCombination());
        renameButton.addActionListener(e -> renameCombination());
        deleteButton.addActionListener(e -> deleteCombination());
        JPanel left = new JPanel(new BorderLayout(0, 6));
        JScrollPane listScroll = new JScrollPane(list);
        listScroll.setPreferredSize(new Dimension(200, 260));
        left.add(listScroll, BorderLayout.CENTER);
        left.add(ButtonSizing.row(6, newButton, renameButton, deleteButton), BorderLayout.SOUTH);

        JPanel parts = new JPanel();
        parts.setLayout(new BoxLayout(parts, BoxLayout.Y_AXIS));
        parts.add(new JLabel("Adds up:"));
        for (String part : TrackerCombination.ALL_PARTS) {
            JCheckBox box = new JCheckBox(part);
            box.setToolTipText(PART_TIPS.get(part));
            box.addActionListener(e -> partsChanged());
            partBoxes.put(part, box);
            parts.add(box);
        }

        JButton save = new JButton("Save", Icons.SAVE);
        save.addActionListener(e -> save());
        JButton cancel = new JButton("Cancel", Icons.CANCEL);
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(ButtonSizing.row(6, save, cancel));

        JPanel content = new JPanel(new BorderLayout(12, 10));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(left, BorderLayout.CENTER);
        content.add(parts, BorderLayout.EAST);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);

        int selected = 0;
        for (int i = 0; i < combinations.size(); i++) {
            if (combinations.get(i).name().equals(selectedName)) {
                selected = i;
            }
        }
        if (!combinations.isEmpty()) {
            list.setSelectedIndex(selected);
        }
        showParts();
        pack();
        setLocationRelativeTo(owner);
    }

    private void showParts() {
        TrackerCombination combination = list.getSelectedValue();
        showing = true;
        try {
            partBoxes.forEach((part, box) -> {
                box.setEnabled(combination != null);
                box.setSelected(combination != null && combination.includes(part));
            });
        } finally {
            showing = false;
        }
        renameButton.setEnabled(combination != null);
        deleteButton.setEnabled(combination != null);
    }

    private void partsChanged() {
        int index = list.getSelectedIndex();
        if (showing || index < 0) {
            return;
        }
        List<String> chosen = partBoxes.entrySet().stream().filter(entry -> entry.getValue().isSelected())
                .map(Map.Entry::getKey).toList();
        combinations.set(index, new TrackerCombination(combinations.get(index).name(), chosen));
    }

    private void addCombination() {
        String name = askName("New Combination", "");
        if (name != null) {
            combinations.addElement(new TrackerCombination(name, List.of(TrackerCombination.WALLET)));
            list.setSelectedIndex(combinations.size() - 1);
        }
    }

    private void renameCombination() {
        int index = list.getSelectedIndex();
        if (index < 0) {
            return;
        }
        TrackerCombination combination = combinations.get(index);
        String name = askName("Rename Combination", combination.name());
        if (name != null) {
            combinations.set(index, new TrackerCombination(name, combination.parts()));
        }
    }

    private void deleteCombination() {
        int index = list.getSelectedIndex();
        if (index >= 0) {
            combinations.remove(index);
            list.setSelectedIndex(Math.min(index, combinations.size() - 1));
            showParts();
        }
    }

    private String askName(String title, String initial) {
        Object answer = JOptionPane.showInputDialog(this, "Name:", title, JOptionPane.PLAIN_MESSAGE, null, null,
                initial);
        if (answer == null) {
            return null;
        }
        String name = answer.toString().strip();
        String problem = nameProblem(name, initial);
        if (problem != null) {
            JOptionPane.showMessageDialog(this, problem, title, JOptionPane.WARNING_MESSAGE);
            return null;
        }
        return name;
    }

    private String nameProblem(String name, String current) {
        if (name.isEmpty()) {
            return "Type a name.";
        }
        for (int i = 0; i < combinations.size(); i++) {
            String other = combinations.get(i).name();
            if (!other.equals(current) && other.toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT))) {
                return "There is already a combination called \"" + other + "\".";
            }
        }
        return null;
    }

    private void save() {
        List<TrackerCombination> saved = new ArrayList<>();
        for (int i = 0; i < combinations.size(); i++) {
            TrackerCombination combination = combinations.get(i);
            if (combination.parts().isEmpty()) {
                list.setSelectedIndex(i);
                JOptionPane.showMessageDialog(this, "Tick at least one thing for \"" + combination.name() + "\" "
                        + "to add up, or delete it.", TITLE, JOptionPane.WARNING_MESSAGE);
                return;
            }
            saved.add(combination);
        }
        service.saveCombinations(saved);
        TrackerCombination selected = list.getSelectedValue();
        dispose();
        onSaved.accept(selected == null ? null : selected.name());
    }
}
