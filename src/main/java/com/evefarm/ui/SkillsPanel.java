package com.evefarm.ui;

import com.evefarm.AppContext;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.model.CharacterAccelerator;
import com.evefarm.model.CharacterAttributes;
import com.evefarm.model.CharacterSkills;
import com.evefarm.model.EveCharacter;
import com.evefarm.model.OwnedSkill;
import com.evefarm.model.PlanRow;
import com.evefarm.model.SkillInfo;
import com.evefarm.model.SkillPlan;
import com.evefarm.model.SkillPlanEntry;
import com.evefarm.model.SkillRequirement;
import com.evefarm.service.AcceleratorTracking;
import com.evefarm.service.InjectorCalculator;
import com.evefarm.service.PlanCosts;
import com.evefarm.service.SkillCatalogService;
import com.evefarm.service.SkillPlanText;
import com.evefarm.service.SkillPlanner;
import com.evefarm.ui.column.ColumnCopySupport;
import com.evefarm.ui.column.ColumnVisibilitySupport;
import com.evefarm.util.IskFormatter;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class SkillsPanel extends JPanel {

    private static final Logger LOG = Logger.getLogger(SkillsPanel.class.getName());
    private static final String TABLE_KEY = "skillPlan";
    private static final int DEFAULT_DIVIDER = 230;

    private final AppContext appContext;
    private final JComboBox<String> characterCombo = new JComboBox<>();
    private final JLabel attributesLabel = new JLabel(" ");
    private final JLabel spLabel = new JLabel(" ");
    private final JLabel acceleratorLabel = new JLabel(" ");
    private final JButton acceleratorButton = new JButton("Accelerator...");
    private final JLabel hintLabel = new JLabel(" ");
    private final DefaultListModel<SkillPlan> planListModel = new DefaultListModel<>();
    private final JList<SkillPlan> planList = new JList<>(planListModel);
    private final JButton newPlanButton = new JButton("New...", Icons.ADD);
    private final JButton renamePlanButton = new JButton("Rename...");
    private final JButton copyPlanButton = new JButton("Copy...");
    private final JButton deletePlanButton = new JButton("Delete", Icons.REMOVE);
    private final JButton addSkillButton = new JButton("Add Skills...", Icons.ADD);
    private final JButton removeButton = new JButton("Remove", Icons.REMOVE);
    private final JButton moveUpButton = new JButton("Move Up");
    private final JButton moveDownButton = new JButton("Move Down");
    private final JButton notesButton = new JButton("Notes...");
    private final JButton importButton = new JButton("Import...", Icons.LOAD);
    private final JButton queueButton = new JButton("From Skill Queue", Icons.LOAD);
    private final JButton exportButton = new JButton("Copy as Text", Icons.CLIPBOARD);
    private final PlanTableModel tableModel = new PlanTableModel();
    private final JTable table = new JTable(tableModel);
    private final JLabel totalsLabel = new JLabel(" ");
    private final JLabel booksLabel = new JLabel(" ");
    private final JLabel injectorsLabel = new JLabel(" ");
    private final JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
    private final Timer hintClearer = new Timer(4000, e -> hintLabel.setText(" "));
    private final AtomicInteger loadGeneration = new AtomicInteger();
    private final AtomicInteger costsGeneration = new AtomicInteger();

    private List<EveCharacter> characters = List.of();
    private String mainCharacterName;
    private Map<Integer, SkillInfo> catalog = Map.of();
    private CharacterSkills skills;
    private CharacterAttributes attributes = CharacterAttributes.NONE;
    private CharacterAccelerator accelerator;
    private Map<Integer, OwnedSkill> owned = Map.of();
    private Map<Integer, Integer> trained = Map.of();
    private SkillPlan plan;
    private List<SkillPlanEntry> entries = List.of();
    private boolean updatingCharacters;
    private boolean updatingPlans;

    public SkillsPanel(AppContext appContext) {
        this.appContext = appContext;
        buildUi();
        postInit();
    }

    private void postInit() {
        TableStyler.style(table);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        ColumnVisibilitySupport.install(table, tableModel, TABLE_KEY, appContext.tableColumnStateDao);
        ColumnCopySupport.install(table, tableModel);
        table.getSelectionModel().addListSelectionListener(e -> updateButtons());

        characterCombo.setRenderer(MainCharacterMarks.comboRenderer(() -> mainCharacterName));
        characterCombo.addActionListener(e -> {
            if (!updatingCharacters) {
                loadCharacter();
            }
        });
        planList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        planList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !updatingPlans) {
                showPlan(planList.getSelectedValue());
            }
        });

        newPlanButton.addActionListener(e -> newPlan());
        renamePlanButton.addActionListener(e -> renamePlan());
        copyPlanButton.addActionListener(e -> copyPlan());
        deletePlanButton.addActionListener(e -> deletePlan());
        addSkillButton.addActionListener(e -> addSkills());
        removeButton.addActionListener(e -> removeSelected());
        moveUpButton.addActionListener(e -> moveSelected(-1));
        moveDownButton.addActionListener(e -> moveSelected(1));
        notesButton.addActionListener(e -> editNotes());
        importButton.addActionListener(e -> importText());
        queueButton.addActionListener(e -> importQueue());
        acceleratorButton.putClientProperty("JButton.buttonType", "toolBarButton");
        acceleratorButton.setToolTipText("Set when the cerebral accelerator ends, as EVE shows it");
        acceleratorButton.setVisible(false);
        acceleratorButton.addActionListener(e -> editAccelerator());
        queueButton.setToolTipText("Adds the skills in the character's training queue in EVE to this plan");
        exportButton.addActionListener(e -> exportText());
        hintClearer.setRepeats(false);
        DividerMemory.install(split, appContext.settingsDao, SettingsDao.SKILLS_DIVIDER, DEFAULT_DIVIDER);
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
        String previous = (String) characterCombo.getSelectedItem();
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        characters.forEach(character -> model.addElement(character.characterName()));
        updatingCharacters = true;
        try {
            characterCombo.setModel(model);
            if (!selectMain && previous != null && model.getIndexOf(previous) >= 0) {
                characterCombo.setSelectedItem(previous);
            } else if (model.getSize() > 0) {
                characterCombo.setSelectedIndex(0);
            }
        } finally {
            updatingCharacters = false;
        }
        loadCharacter();
    }

    private EveCharacter selectedCharacter() {
        Object name = characterCombo.getSelectedItem();
        return characters.stream().filter(character -> character.characterName().equals(name))
                .findFirst().orElse(null);
    }

    private record Loaded(Map<Integer, SkillInfo> catalog, CharacterSkills skills, CharacterAccelerator accelerator,
                          List<SkillPlan> plans) {
    }

    private void loadCharacter() {
        EveCharacter character = selectedCharacter();
        int generation = loadGeneration.incrementAndGet();
        Long previousPlan = plan == null ? null : plan.planId();
        new SwingWorker<Loaded, Void>() {
            @Override
            protected Loaded doInBackground() {
                return loadData(character);
            }

            @Override
            protected void done() {
                if (generation != loadGeneration.get()) {
                    return;
                }
                try {
                    apply(get(), previousPlan);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Failed to load the skills tab", e);
                    hintLabel.setText("Couldn't load the skills - see the log");
                }
            }
        }.execute();
    }

    private Loaded loadData(EveCharacter character) {
        Map<Integer, SkillInfo> skillCatalog = appContext.skillCatalogService.catalog();
        if (character == null) {
            return new Loaded(skillCatalog, null, null, List.of());
        }
        CharacterSkills characterSkills = appContext.skillService.skills(character.characterId()).orElse(null);
        CharacterAccelerator characterAccelerator = characterSkills == null ? null
                : appContext.skillService.accelerator(character.characterId()).orElse(null);
        return new Loaded(skillCatalog, characterSkills, characterAccelerator,
                appContext.skillService.plans(character.characterId()));
    }

    private void apply(Loaded loaded, Long previousPlan) {
        catalog = loaded.catalog();
        skills = loaded.skills();
        attributes = skills == null ? CharacterAttributes.NONE : skills.attributes();
        accelerator = loaded.accelerator();
        owned = skills == null ? Map.of() : skills.skills().stream()
                .collect(Collectors.toMap(OwnedSkill::skillId, skill -> skill, (a, b) -> a));
        trained = new HashMap<>();
        owned.values().forEach(skill -> trained.put(skill.skillId(), skill.trainedLevel()));
        updateCharacterLabels();

        updatingPlans = true;
        try {
            planListModel.clear();
            loaded.plans().forEach(planListModel::addElement);
            int index = 0;
            for (int i = 0; i < planListModel.size(); i++) {
                if (previousPlan != null && planListModel.get(i).planId() == previousPlan) {
                    index = i;
                }
            }
            if (!planListModel.isEmpty()) {
                planList.setSelectedIndex(index);
            }
        } finally {
            updatingPlans = false;
        }
        showPlan(planList.getSelectedValue());
    }

    private void updateCharacterLabels() {
        EveCharacter character = selectedCharacter();
        if (character == null) {
            attributesLabel.setText("Add a character first from File > Characters.");
            spLabel.setText(" ");
            updateAcceleratorLabel();
            return;
        }
        if (skills == null) {
            attributesLabel.setText("No skills saved yet for " + character.characterName()
                    + ". Run Update > Skills.");
            spLabel.setText(" ");
            updateAcceleratorLabel();
            return;
        }
        String text = CharacterAttributes.NAMES.stream()
                .map(name -> name + " " + attributes.get(name))
                .collect(Collectors.joining("  ·  "));
        if (owned.values().stream().anyMatch(skill -> skill.activeLevel() < skill.trainedLevel())) {
            text += "  ·  Alpha clone - times assume Omega";
        }
        attributesLabel.setText(text);
        updateAcceleratorLabel();
        spLabel.setText(String.format(Locale.US, "%,d SP  ·  %,d unallocated  ·  %s", skills.totalSp(),
                skills.unallocatedSp(), remapText()));
    }

    private void updateAcceleratorLabel() {
        acceleratorButton.setVisible(accelerator != null);
        if (accelerator == null) {
            acceleratorLabel.setText(" ");
            return;
        }
        String name = (accelerator.name() == null ? "Accelerator" : accelerator.name()) + " +" + accelerator.bonus();
        Instant end = accelerator.endsAt();
        if (end == null) {
            acceleratorLabel.setText(name + "  ·  end unknown - training times assume it lasts");
        } else if (end.isBefore(Instant.now())) {
            acceleratorLabel.setText(name + "  ·  ended " + PlanTableModel.formatDate(end));
        } else {
            acceleratorLabel.setText(name + "  ·  until " + (accelerator.setByUser() ? "" : "about ")
                    + PlanTableModel.formatDate(end));
        }
    }

    private void editAccelerator() {
        CharacterAccelerator current = accelerator;
        if (current == null) {
            return;
        }
        String initial = current.endsAt() == null ? "" : PlanTableModel.formatDate(current.endsAt());
        Object answer = JOptionPane.showInputDialog(this, "<html>When does the +" + current.bonus()
                        + " accelerator end?<br>Type the time left that EVE shows under Active Boosters, for example "
                        + "<b>505:31:31</b> or <b>6d 4h</b>,<br>or the end date and time, for example "
                        + "<b>2026-10-25 18:00</b>.</html>",
                "Accelerator", JOptionPane.PLAIN_MESSAGE, null, null, initial);
        if (answer == null) {
            return;
        }
        Instant end = AcceleratorTracking.parseEnd(answer.toString(), Instant.now(), ZoneId.systemDefault());
        if (end == null) {
            JOptionPane.showMessageDialog(this, "That isn't a time left or a date EVE Farm understands.",
                    "Accelerator", JOptionPane.WARNING_MESSAGE);
            return;
        }
        appContext.skillService.setAcceleratorEnd(current, end);
        accelerator = current.withEnd(end, true);
        updateAcceleratorLabel();
        refreshTable();
    }

    private String remapText() {
        int bonus = skills.bonusRemaps() == null ? 0 : skills.bonusRemaps();
        String next;
        try {
            Instant cooldown = skills.remapCooldownDate() == null ? null : Instant.parse(skills.remapCooldownDate());
            next = cooldown == null || cooldown.isBefore(Instant.now()) ? "remap available"
                    : "next remap " + PlanTableModel.formatDate(cooldown);
        } catch (DateTimeParseException e) {
            next = "remap date unknown";
        }
        return bonus > 0 ? next + "  ·  " + bonus + " bonus remap" + (bonus == 1 ? "" : "s") : next;
    }

    private void showPlan(SkillPlan selected) {
        plan = selected;
        List<SkillPlanEntry> loaded = plan == null ? List.of() : appContext.skillService.entries(plan);
        List<SkillPlanEntry> remaining = SkillPlanner.withoutTrained(loaded, trained);
        if (plan != null && remaining.size() != loaded.size()) {
            appContext.skillService.saveEntries(plan, remaining);
            flashHint((loaded.size() - remaining.size()) + " trained skill level(s) were taken off the plan.");
        }
        entries = remaining;
        refreshTable();
    }

    private void setEntries(List<SkillPlanEntry> updated) {
        entries = updated;
        appContext.skillService.saveEntries(plan, entries);
        refreshTable();
    }

    private void refreshTable() {
        long totalSp = skills == null ? 0 : skills.totalSp();
        List<PlanRow> rows = SkillPlanner.rows(entries, catalog, owned, attributes,
                accelerator == null ? 0 : accelerator.bonus(), accelerator == null ? null : accelerator.endsAt(),
                totalSp, Instant.now());
        tableModel.setRows(rows);
        TableStyler.packColumns(table);
        updateTotals(rows);
        updateButtons();
    }

    private void updateTotals(List<PlanRow> rows) {
        if (plan == null) {
            totalsLabel.setText(planListModel.isEmpty() ? "Create a plan with New... on the left." : " ");
            booksLabel.setText(" ");
            injectorsLabel.setText(" ");
            return;
        }
        if (rows.isEmpty()) {
            totalsLabel.setText("The plan is empty. Click Add Skills... or Import...");
            booksLabel.setText(" ");
            injectorsLabel.setText(" ");
            return;
        }
        Duration total = rows.stream().map(PlanRow::time).reduce(Duration.ZERO, Duration::plus);
        long sp = rows.stream().mapToLong(PlanRow::spNeeded).sum();
        totalsLabel.setText(String.format(Locale.US, "%d skill level%s  ·  %,d SP  ·  %s  ·  finishes %s",
                rows.size(), rows.size() == 1 ? "" : "s", sp, PlanTableModel.formatDuration(total),
                PlanTableModel.formatDate(rows.getLast().finish())));
        updateCosts(sp);
    }

    private void updateCosts(long planSp) {
        Set<Integer> toBuy = new LinkedHashSet<>();
        entries.stream().map(SkillPlanEntry::skillId).filter(id -> !owned.containsKey(id)).forEach(toBuy::add);
        long trainedSp = skills == null ? 0 : skills.totalSp();
        long unallocatedSp = skills == null ? 0 : skills.unallocatedSp();
        int generation = costsGeneration.incrementAndGet();
        booksLabel.setText(toBuy.isEmpty() ? "No skill books to buy."
                : "Skill books to buy: " + toBuy.size() + " (pricing...)");
        injectorsLabel.setText(skills == null ? " " : "Skill injectors to finish it now: pricing...");
        new SwingWorker<PlanCosts, Void>() {
            @Override
            protected PlanCosts doInBackground() {
                return PlanCosts.estimate(appContext.priceService, toBuy, planSp, trainedSp, unallocatedSp);
            }

            @Override
            protected void done() {
                if (generation != costsGeneration.get()) {
                    return;
                }
                try {
                    PlanCosts costs = get();
                    if (!toBuy.isEmpty()) {
                        booksLabel.setText("Skill books to buy: " + toBuy.size() + " ("
                                + IskFormatter.format(costs.skillBooks()) + ")");
                    }
                    if (skills != null) {
                        injectorsLabel.setText(injectorText(costs));
                    }
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Failed to price the skill plan", e);
                    booksLabel.setText(toBuy.isEmpty() ? "No skill books to buy."
                            : "Skill books to buy: " + toBuy.size() + " (price unknown)");
                    injectorsLabel.setText(" ");
                }
            }
        }.execute();
    }

    private static String injectorText(PlanCosts costs) {
        InjectorCalculator.InjectorPlan injectors = costs.injectors();
        if (injectors.spToInject() <= 0) {
            return "The unallocated skill points cover the whole plan.";
        }
        List<String> parts = new ArrayList<>();
        if (injectors.large() > 0) {
            parts.add(injectors.large() + " Large");
        }
        if (injectors.small() > 0) {
            parts.add(injectors.small() + " Small");
        }
        double cost = costs.injectorsPrice();
        return String.format(Locale.US, "Skill injectors to finish it now: %s (%,d SP)  ·  %s",
                String.join(" + ", parts), injectors.spInjected(),
                cost > 0 ? IskFormatter.format(cost) : "price unknown");
    }

    private void updateButtons() {
        boolean hasCharacter = selectedCharacter() != null;
        boolean hasPlan = plan != null;
        int[] rows = table.getSelectedRows();
        newPlanButton.setEnabled(hasCharacter);
        renamePlanButton.setEnabled(hasPlan);
        copyPlanButton.setEnabled(hasPlan);
        deletePlanButton.setEnabled(hasPlan);
        addSkillButton.setEnabled(hasPlan);
        importButton.setEnabled(hasPlan);
        queueButton.setEnabled(hasPlan);
        exportButton.setEnabled(hasPlan && !entries.isEmpty());
        removeButton.setEnabled(hasPlan && rows.length > 0);
        moveUpButton.setEnabled(hasPlan && rows.length == 1 && rows[0] > 0);
        moveDownButton.setEnabled(hasPlan && rows.length == 1 && rows[0] < entries.size() - 1);
        notesButton.setEnabled(hasPlan && rows.length == 1);
    }

    private String askName(String title, String initial) {
        Object answer = JOptionPane.showInputDialog(this, "Plan name:", title, JOptionPane.PLAIN_MESSAGE, null,
                null, initial);
        if (answer == null) {
            return null;
        }
        String name = answer.toString().strip();
        return name.isEmpty() ? null : name;
    }

    private void newPlan() {
        EveCharacter character = selectedCharacter();
        String name = character == null ? null : askName("New Skill Plan", "Plan " + (planListModel.size() + 1));
        if (name != null) {
            runPlanChange(() -> appContext.skillService.createPlan(character.characterId(), name));
        }
    }

    private void renamePlan() {
        SkillPlan current = plan;
        String name = current == null ? null : askName("Rename Skill Plan", current.name());
        if (name != null) {
            runPlanChange(() -> {
                appContext.skillService.renamePlan(current, name);
                return new SkillPlan(current.planId(), current.characterId(), name);
            });
        }
    }

    private void copyPlan() {
        SkillPlan current = plan;
        String name = current == null ? null : askName("Copy Skill Plan", current.name() + " (copy)");
        if (name != null) {
            runPlanChange(() -> appContext.skillService.copyPlan(current, name));
        }
    }

    private void deletePlan() {
        SkillPlan current = plan;
        if (current == null || JOptionPane.showConfirmDialog(this, "Delete the plan '" + current.name() + "'?",
                "Delete Skill Plan", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        runPlanChange(() -> {
            appContext.skillService.deletePlan(current);
            return null;
        });
    }

    private interface PlanChange {
        SkillPlan run();
    }

    private void runPlanChange(PlanChange change) {
        try {
            SkillPlan selected = change.run();
            plan = selected;
            loadCharacter();
        } catch (RuntimeException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Skill Plan", JOptionPane.WARNING_MESSAGE);
        }
    }

    private boolean catalogReady() {
        if (!catalog.isEmpty()) {
            return true;
        }
        JOptionPane.showMessageDialog(this, "The list of EVE's skills isn't downloaded yet.\n"
                + "Run Update > Skills once, then try again.", "Skills", JOptionPane.INFORMATION_MESSAGE);
        return false;
    }

    private boolean skillsReady() {
        if (skills != null) {
            return true;
        }
        EveCharacter character = selectedCharacter();
        String name = character == null ? "this character" : character.characterName();
        JOptionPane.showMessageDialog(this, "No skills are saved for " + name + " yet.\n"
                + "Run Update > Skills first, so the plan knows what " + name + " has trained and how fast "
                + "it learns.", "Skills", JOptionPane.INFORMATION_MESSAGE);
        return false;
    }

    private void addSkills() {
        if (plan == null || !catalogReady() || !skillsReady()) {
            return;
        }
        new SkillBrowserDialog(SwingUtilities.getWindowAncestor(this), plan.name(), catalog, trained,
                new SkillBrowserDialog.PlanAccess() {
                    @Override
                    public int plannedLevel(int skillId) {
                        return entries.stream().filter(entry -> entry.skillId() == skillId)
                                .mapToInt(SkillPlanEntry::level).max().orElse(0);
                    }

                    @Override
                    public void add(int skillId, int level) {
                        setEntries(SkillPlanner.add(entries, catalog, trained, skillId, level));
                    }
                }).setVisible(true);
    }

    private void removeSelected() {
        int[] rows = table.getSelectedRows();
        if (rows.length == 0) {
            return;
        }
        List<SkillPlanEntry> selected = new ArrayList<>();
        for (int row : rows) {
            selected.add(entries.get(table.convertRowIndexToModel(row)));
        }
        List<SkillPlanEntry> updated = entries;
        for (SkillPlanEntry entry : selected) {
            int index = updated.indexOf(entry);
            if (index >= 0) {
                updated = SkillPlanner.remove(updated, catalog, trained, index);
            }
        }
        int extra = entries.size() - updated.size() - selected.size();
        if (extra > 0 && JOptionPane.showConfirmDialog(this, "This also takes off " + extra
                        + " skill level(s) that need it, or that only the removed skills needed. Continue?",
                "Remove Skills", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        setEntries(updated);
    }

    private void moveSelected(int delta) {
        int[] rows = table.getSelectedRows();
        if (rows.length != 1) {
            return;
        }
        int from = rows[0];
        int to = from + delta;
        List<SkillPlanEntry> moved = SkillPlanner.move(entries, catalog, trained, from, to);
        if (moved == entries) {
            Toolkit.getDefaultToolkit().beep();
            flashHint(delta < 0 ? "It can't go above a skill it needs." : "It can't go below a skill that needs it.");
            return;
        }
        setEntries(moved);
        table.setRowSelectionInterval(to, to);
        table.setColumnSelectionInterval(0, table.getColumnCount() - 1);
    }

    private void editNotes() {
        int[] rows = table.getSelectedRows();
        if (rows.length != 1) {
            return;
        }
        int index = rows[0];
        SkillPlanEntry entry = entries.get(index);
        Object answer = JOptionPane.showInputDialog(this, "Notes:", "Skill Notes", JOptionPane.PLAIN_MESSAGE,
                null, null, entry.notes() == null ? "" : entry.notes());
        if (answer == null) {
            return;
        }
        String notes = answer.toString().strip();
        List<SkillPlanEntry> updated = new ArrayList<>(entries);
        updated.set(index, entry.withNotes(notes.isEmpty() ? null : notes));
        setEntries(updated);
    }

    private void importText() {
        if (plan == null || !catalogReady() || !skillsReady()) {
            return;
        }
        JTextArea area = new JTextArea(16, 50);
        JScrollPane scroll = new JScrollPane(area);
        JPanel content = new JPanel(new BorderLayout(0, 6));
        content.add(new JLabel("<html>Paste a skill list, one skill per line (for example <b>Caldari Cruiser 4</b> "
                + "or <b>Caldari Cruiser IV</b>),<br>or a ship fit copied from EVE. The skills it needs are added "
                + "with their prerequisites.</html>"), BorderLayout.NORTH);
        content.add(scroll, BorderLayout.CENTER);
        if (JOptionPane.showConfirmDialog(this, content, "Import into " + plan.name(),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        String text = area.getText();
        if (text.isBlank()) {
            return;
        }
        importButton.setEnabled(false);
        new SwingWorker<SkillCatalogService.ItemRequirements, Void>() {
            @Override
            protected SkillCatalogService.ItemRequirements doInBackground() {
                if (SkillPlanText.isFit(text)) {
                    return appContext.skillCatalogService.requirementsOfItems(SkillPlanText.fitItemNames(text));
                }
                SkillPlanText.ParsedSkills parsed = SkillPlanText.parseSkills(text, catalog);
                return new SkillCatalogService.ItemRequirements(parsed.skills(), parsed.unknownLines());
            }

            @Override
            protected void done() {
                updateButtons();
                try {
                    applyImport(get());
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Failed to import into the skill plan", e);
                    JOptionPane.showMessageDialog(SkillsPanel.this, "Couldn't import: " + e.getMessage(),
                            "Import", JOptionPane.WARNING_MESSAGE);
                }
            }
        }.execute();
    }

    private record QueueImport(Loaded loaded, List<SkillRequirement> queue) {
    }

    private void importQueue() {
        EveCharacter character = selectedCharacter();
        if (plan == null || character == null || !catalogReady()) {
            return;
        }
        long planId = plan.planId();
        queueButton.setEnabled(false);
        new SwingWorker<QueueImport, Void>() {
            @Override
            protected QueueImport doInBackground() {
                List<SkillRequirement> queue = appContext.skillService.skillQueue(character.characterId());
                appContext.skillService.refreshSkillsForCharacter(character.characterId());
                return new QueueImport(loadData(character), queue);
            }

            @Override
            protected void done() {
                updateButtons();
                try {
                    QueueImport result = get();
                    apply(result.loaded(), planId);
                    List<SkillRequirement> queue = result.queue();
                    if (queue.isEmpty()) {
                        JOptionPane.showMessageDialog(SkillsPanel.this, "The training queue of "
                                        + character.characterName() + " is empty in EVE.", "Skill Queue",
                                JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }
                    applyImport(new SkillCatalogService.ItemRequirements(queue, List.of()));
                } catch (Exception e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    LOG.log(Level.WARNING, "Failed to read the skill queue", cause);
                    JOptionPane.showMessageDialog(SkillsPanel.this, "Couldn't read the skill queue: "
                            + cause.getMessage(), "Skill Queue", JOptionPane.WARNING_MESSAGE);
                }
            }
        }.execute();
    }

    private void applyImport(SkillCatalogService.ItemRequirements imported) {
        int before = entries.size();
        List<SkillPlanEntry> updated = entries;
        for (SkillRequirement requirement : imported.requirements()) {
            if (catalog.containsKey(requirement.skillId())) {
                updated = SkillPlanner.add(updated, catalog, trained, requirement.skillId(), requirement.level());
            }
        }
        setEntries(updated);
        StringBuilder message = new StringBuilder("Added " + (updated.size() - before) + " skill level(s).");
        if (!imported.unknownNames().isEmpty()) {
            message.append("\n\nNot recognized:\n");
            imported.unknownNames().stream().limit(15).forEach(name -> message.append(name).append('\n'));
            if (imported.unknownNames().size() > 15) {
                message.append("...");
            }
        }
        JOptionPane.showMessageDialog(this, message.toString().strip(), "Import", JOptionPane.INFORMATION_MESSAGE);
    }

    private void exportText() {
        String text = SkillPlanText.export(entries, catalog);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        flashHint("Copied " + entries.size() + " skill level(s) as text, one per line.");
    }

    private void flashHint(String text) {
        hintLabel.setText(text);
        hintClearer.restart();
    }

    private void buildUi() {
        setLayout(new BorderLayout());

        JPanel characterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        characterRow.add(new JLabel("Character:"));
        characterCombo.setPreferredSize(new Dimension(180, characterCombo.getPreferredSize().height));
        characterRow.add(characterCombo);
        characterRow.add(Box.createHorizontalStrut(12));
        characterRow.add(attributesLabel);
        JPanel spRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        spRow.add(spLabel);
        spRow.add(Box.createHorizontalStrut(12));
        spRow.add(acceleratorLabel);
        spRow.add(acceleratorButton);
        spRow.add(Box.createHorizontalStrut(12));
        spRow.add(hintLabel);
        JPanel top = new JPanel(new GridLayout(0, 1, 0, 4));
        top.setBorder(BorderFactory.createEmptyBorder(8, 4, 4, 8));
        top.add(characterRow);
        top.add(spRow);

        JLabel plansTitle = new JLabel("Plans");
        plansTitle.setFont(plansTitle.getFont().deriveFont(Font.BOLD));
        JPanel planButtons = new JPanel(new GridLayout(2, 2, 4, 4));
        planButtons.add(newPlanButton);
        planButtons.add(renamePlanButton);
        planButtons.add(copyPlanButton);
        planButtons.add(deletePlanButton);
        JPanel left = new JPanel(new BorderLayout(0, 6));
        left.setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 4));
        left.add(plansTitle, BorderLayout.NORTH);
        left.add(new JScrollPane(planList), BorderLayout.CENTER);
        left.add(planButtons, BorderLayout.SOUTH);

        JPanel planToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        for (JButton button : List.of(addSkillButton, removeButton, moveUpButton, moveDownButton, notesButton,
                importButton, queueButton, exportButton)) {
            planToolbar.add(button);
        }
        JPanel totals = new JPanel(new GridLayout(0, 1, 0, 2));
        totals.add(totalsLabel);
        totals.add(booksLabel);
        totals.add(injectorsLabel);
        JPanel right = new JPanel(new BorderLayout(0, 6));
        right.setBorder(BorderFactory.createEmptyBorder(4, 4, 8, 8));
        right.add(planToolbar, BorderLayout.NORTH);
        right.add(new JScrollPane(table), BorderLayout.CENTER);
        right.add(totals, BorderLayout.SOUTH);

        split.setLeftComponent(left);
        split.setRightComponent(right);
        split.setBorder(BorderFactory.createEmptyBorder());

        add(top, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
        updateButtons();
    }
}
