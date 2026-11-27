package com.evefarm.ui;

import com.evefarm.model.EveCharacter;
import com.evefarm.service.ForecastSettings.Group;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

final class CharacterGroupsDialog extends JDialog {

    private static final String TITLE = "Character Groups";

    private final Map<String, Set<Long>> groups = new LinkedHashMap<>();
    private final DefaultListModel<String> names = new DefaultListModel<>();
    private final JList<String> list = new JList<>(names);
    private final Map<Long, JCheckBox> boxes = new LinkedHashMap<>();
    private final JButton renameButton = new JButton("Rename...");
    private final JButton deleteButton = new JButton("Delete", Icons.REMOVE);
    private final Consumer<List<Group>> onSave;
    private boolean showing;

    CharacterGroupsDialog(Window owner, List<EveCharacter> characters, List<Group> saved,
                          Consumer<List<Group>> onSave) {
        super(owner, TITLE, ModalityType.APPLICATION_MODAL);
        this.onSave = onSave;
        saved.forEach(group -> groups.put(group.name(), new LinkedHashSet<>(group.characterIds())));
        groups.keySet().forEach(names::addElement);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.putClientProperty("html.disable", Boolean.TRUE);
        list.addListSelectionListener(e -> showMembers());
        JButton newButton = new JButton("New...", Icons.ADD);
        newButton.addActionListener(e -> addGroup());
        renameButton.addActionListener(e -> renameGroup());
        deleteButton.addActionListener(e -> deleteGroup());
        JScrollPane listScroll = new JScrollPane(list);
        listScroll.setPreferredSize(new Dimension(200, 260));
        JPanel left = new JPanel(new BorderLayout(0, 6));
        left.add(listScroll, BorderLayout.CENTER);
        left.add(ButtonSizing.row(6, newButton, renameButton, deleteButton), BorderLayout.SOUTH);

        JPanel members = new JPanel();
        members.setLayout(new BoxLayout(members, BoxLayout.Y_AXIS));
        for (EveCharacter character : characters) {
            JCheckBox box = new JCheckBox(character.characterName());
            box.putClientProperty("html.disable", Boolean.TRUE);
            box.addActionListener(e -> membersChanged());
            boxes.put(character.characterId(), box);
            members.add(box);
        }
        JScrollPane membersScroll = new JScrollPane(members);
        membersScroll.setPreferredSize(new Dimension(240, 260));
        membersScroll.setBorder(BorderFactory.createEmptyBorder());
        JPanel right = new JPanel(new BorderLayout(0, 6));
        right.add(new JLabel("Characters in the group:"), BorderLayout.NORTH);
        right.add(membersScroll, BorderLayout.CENTER);

        JButton save = new JButton("Save", Icons.SAVE);
        save.addActionListener(e -> save());
        JButton cancel = new JButton("Cancel", Icons.CANCEL);
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(ButtonSizing.row(6, save, cancel));
        getRootPane().setDefaultButton(save);

        JPanel content = new JPanel(new BorderLayout(12, 10));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(left, BorderLayout.CENTER);
        content.add(right, BorderLayout.EAST);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        if (!names.isEmpty()) {
            list.setSelectedIndex(0);
        }
        showMembers();
        pack();
        setLocationRelativeTo(owner);
    }

    private void showMembers() {
        String selected = list.getSelectedValue();
        showing = true;
        Set<Long> members = selected == null ? Set.of() : groups.get(selected);
        boxes.forEach((id, box) -> {
            box.setSelected(members.contains(id));
            box.setEnabled(selected != null);
        });
        showing = false;
        renameButton.setEnabled(selected != null);
        deleteButton.setEnabled(selected != null);
    }

    private void membersChanged() {
        String selected = list.getSelectedValue();
        if (showing || selected == null) {
            return;
        }
        Set<Long> members = new LinkedHashSet<>();
        boxes.forEach((id, box) -> {
            if (box.isSelected()) {
                members.add(id);
            }
        });
        groups.put(selected, members);
    }

    private void addGroup() {
        String name = askName("New group name:", "");
        if (name != null) {
            groups.put(name, new LinkedHashSet<>());
            names.addElement(name);
            list.setSelectedValue(name, true);
        }
    }

    private void renameGroup() {
        String old = list.getSelectedValue();
        String name = old == null ? null : askName("Rename the group to:", old);
        if (name == null || name.equals(old)) {
            return;
        }
        Map<String, Set<Long>> renamed = new LinkedHashMap<>();
        groups.forEach((key, members) -> renamed.put(key.equals(old) ? name : key, members));
        groups.clear();
        groups.putAll(renamed);
        names.set(names.indexOf(old), name);
        list.setSelectedValue(name, true);
    }

    private void deleteGroup() {
        String selected = list.getSelectedValue();
        if (selected == null) {
            return;
        }
        groups.remove(selected);
        names.removeElement(selected);
        if (!names.isEmpty()) {
            list.setSelectedIndex(0);
        }
        showMembers();
    }

    private String askName(String question, String current) {
        Object answer = JOptionPane.showInputDialog(this, question, TITLE, JOptionPane.PLAIN_MESSAGE, null, null,
                current);
        String name = answer == null ? "" : answer.toString().strip();
        if (name.isEmpty()) {
            return null;
        }
        if (!name.equals(current) && groups.containsKey(name)) {
            JOptionPane.showMessageDialog(this, "There is already a group called " + name + ".", TITLE,
                    JOptionPane.WARNING_MESSAGE);
            return null;
        }
        return name;
    }

    private void save() {
        List<Group> result = new ArrayList<>();
        groups.forEach((name, members) -> result.add(new Group(name, List.copyOf(members))));
        onSave.accept(result);
        dispose();
    }
}
