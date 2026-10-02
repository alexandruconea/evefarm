package com.evefarm.ui;

import com.evefarm.model.SkillInfo;
import com.evefarm.model.SkillRequirement;
import com.evefarm.service.SkillPlanText;
import com.evefarm.service.SkillPlanner;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

final class SkillBrowserDialog extends JDialog {

    interface PlanAccess {
        int plannedLevel(int skillId);

        void add(int skillId, int level);
    }

    private final Map<Integer, SkillInfo> catalog;
    private final Map<Integer, Integer> trained;
    private final PlanAccess plan;
    private final JTextField searchField = new JTextField();
    private final DefaultTreeModel treeModel = new DefaultTreeModel(new DefaultMutableTreeNode());
    private final JTree tree = new JTree(treeModel);
    private final JLabel nameLabel = new JLabel(" ");
    private final JLabel infoLabel = new JLabel(" ");
    private final JLabel statusLabel = new JLabel(" ");
    private final JTextArea requirementsArea = new JTextArea();
    private final JTextArea descriptionArea = new JTextArea();
    private final JButton[] levelButtons = new JButton[SkillPlanner.MAX_LEVEL];
    private SkillInfo selected;

    SkillBrowserDialog(Window owner, String planName, Map<Integer, SkillInfo> catalog, Map<Integer, Integer> trained,
                       PlanAccess plan) {
        super(owner, "Add Skills - " + planName, ModalityType.APPLICATION_MODAL);
        this.catalog = catalog;
        this.trained = trained;
        this.plan = plan;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        buildUi();
        rebuildTree();
        showSkill(null);
        setSize(new Dimension(920, 620));
        setLocationRelativeTo(owner);
    }

    private void buildUi() {
        searchField.putClientProperty("JTextField.placeholderText", "Search skills");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                rebuildTree();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                rebuildTree();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                rebuildTree();
            }
        });
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.addTreeSelectionListener(e -> {
            Object node = e.getPath() == null ? null : e.getPath().getLastPathComponent();
            Object value = node instanceof DefaultMutableTreeNode treeNode ? treeNode.getUserObject() : null;
            showSkill(value instanceof SkillNode skillNode ? skillNode.skill : null);
        });

        JPanel left = new JPanel(new BorderLayout(0, 6));
        left.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 4));
        left.add(searchField, BorderLayout.NORTH);
        left.add(new JScrollPane(tree), BorderLayout.CENTER);

        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, nameLabel.getFont().getSize2D() + 3f));
        JPanel header = new JPanel(new GridLayout(0, 1, 0, 4));
        header.add(nameLabel);
        header.add(infoLabel);
        header.add(statusLabel);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        for (int level = 1; level <= SkillPlanner.MAX_LEVEL; level++) {
            int target = level;
            JButton button = new JButton("Plan to " + SkillPlanText.roman(level), Icons.ADD);
            button.addActionListener(e -> addToPlan(target));
            levelButtons[level - 1] = button;
            buttons.add(button);
        }

        for (JTextArea area : List.of(requirementsArea, descriptionArea)) {
            area.setEditable(false);
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            area.setOpaque(false);
            area.setBorder(BorderFactory.createEmptyBorder());
        }
        JPanel texts = new JPanel(new BorderLayout(0, 10));
        texts.add(titled("Requires", requirementsArea), BorderLayout.NORTH);
        texts.add(titled("Description", descriptionArea), BorderLayout.CENTER);

        JPanel right = new JPanel(new BorderLayout(0, 12));
        right.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        right.add(header, BorderLayout.NORTH);
        right.add(new JScrollPane(texts), BorderLayout.CENTER);
        right.add(buttons, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setDividerLocation(320);
        split.setBorder(BorderFactory.createEmptyBorder());

        JButton close = new JButton("Close", Icons.CANCEL);
        close.addActionListener(e -> dispose());
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.add(close);

        setLayout(new BorderLayout());
        add(split, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
    }

    private static JPanel titled(String title, JTextArea area) {
        JLabel label = new JLabel(title);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.add(label, BorderLayout.NORTH);
        panel.add(area, BorderLayout.CENTER);
        return panel;
    }

    private void rebuildTree() {
        String filter = searchField.getText().strip().toLowerCase(Locale.ROOT);
        Map<String, List<SkillInfo>> groups = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (SkillInfo skill : catalog.values()) {
            if (filter.isEmpty() || skill.name().toLowerCase(Locale.ROOT).contains(filter)) {
                groups.computeIfAbsent(skill.groupName() == null ? "Other" : skill.groupName(),
                        name -> new ArrayList<>()).add(skill);
            }
        }
        DefaultMutableTreeNode root = new DefaultMutableTreeNode();
        for (Map.Entry<String, List<SkillInfo>> group : groups.entrySet()) {
            DefaultMutableTreeNode groupNode = new DefaultMutableTreeNode(group.getKey());
            group.getValue().stream()
                    .sorted(Comparator.comparing(SkillInfo::name, String.CASE_INSENSITIVE_ORDER))
                    .forEach(skill -> groupNode.add(new DefaultMutableTreeNode(new SkillNode(skill))));
            root.add(groupNode);
        }
        treeModel.setRoot(root);
        if (!filter.isEmpty()) {
            for (int i = 0; i < root.getChildCount(); i++) {
                tree.expandPath(new TreePath(((DefaultMutableTreeNode) root.getChildAt(i)).getPath()));
            }
        }
    }

    private void showSkill(SkillInfo skill) {
        selected = skill;
        if (skill == null) {
            nameLabel.setText("Choose a skill");
            infoLabel.setText(" ");
            statusLabel.setText(" ");
            requirementsArea.setText("");
            descriptionArea.setText("");
            for (JButton button : levelButtons) {
                button.setEnabled(false);
            }
            return;
        }
        int have = trained.getOrDefault(skill.skillId(), 0);
        int planned = plan.plannedLevel(skill.skillId());
        nameLabel.setText(skill.name());
        infoLabel.setText(skill.groupName() + "  ·  Rank " + skill.rank() + "  ·  " + skill.primaryAttribute()
                + " / " + skill.secondaryAttribute());
        String status = have > 0 ? "Trained to level " + SkillPlanText.roman(have) : "Not trained";
        if (planned > have) {
            status += "  ·  planned to " + SkillPlanText.roman(planned);
        }
        statusLabel.setText(status);
        requirementsArea.setText(requirementsText(skill));
        descriptionArea.setText(skill.description());
        descriptionArea.setCaretPosition(0);
        for (int level = 1; level <= SkillPlanner.MAX_LEVEL; level++) {
            levelButtons[level - 1].setEnabled(level > Math.max(have, planned));
        }
    }

    private String requirementsText(SkillInfo skill) {
        if (skill.requirements().isEmpty()) {
            return "Nothing - you can train it straight away.";
        }
        StringBuilder text = new StringBuilder();
        for (SkillRequirement requirement : skill.requirements()) {
            SkillInfo required = catalog.get(requirement.skillId());
            String name = required == null ? "Skill #" + requirement.skillId() : required.name();
            int have = trained.getOrDefault(requirement.skillId(), 0);
            int planned = plan.plannedLevel(requirement.skillId());
            String state = have >= requirement.level() ? "trained"
                    : planned >= requirement.level() ? "planned" : "missing";
            text.append(name).append(' ').append(SkillPlanText.roman(requirement.level()))
                    .append("  -  ").append(state).append('\n');
        }
        return text.toString().strip();
    }

    private void addToPlan(int level) {
        if (selected == null) {
            return;
        }
        plan.add(selected.skillId(), level);
        showSkill(selected);
        tree.repaint();
    }

    private final class SkillNode {

        private final SkillInfo skill;

        private SkillNode(SkillInfo skill) {
            this.skill = skill;
        }

        @Override
        public String toString() {
            int have = trained.getOrDefault(skill.skillId(), 0);
            int planned = plan.plannedLevel(skill.skillId());
            String text = skill.name();
            if (have > 0) {
                text += " (" + SkillPlanText.roman(have) + ")";
            }
            if (planned > have) {
                text += " -> " + SkillPlanText.roman(planned);
            }
            return text;
        }
    }
}
