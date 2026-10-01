package com.evefarm.ui;

import com.evefarm.AppContext;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.model.AbyssFleet;
import com.evefarm.model.AbyssTier;
import com.evefarm.model.AbyssWeather;
import com.evefarm.model.AbyssalCargo;
import com.evefarm.model.AbyssalLoot;
import com.evefarm.model.AbyssalRun;
import com.evefarm.model.EveCharacter;
import com.evefarm.service.AbyssLootService;
import com.evefarm.service.ItemIconService;
import com.evefarm.util.IskFormatter;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

final class AbyssRunDialog extends JDialog {

    private static final Logger LOG = Logger.getLogger(AbyssRunDialog.class.getName());
    private static final DateTimeFormatter STARTED_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private record CharacterChoice(long id, String name) {
        @Override
        public String toString() {
            return name;
        }
    }

    private final AppContext appContext;
    private final AbyssalRun original;
    private final Runnable onSaved;
    private final JComboBox<CharacterChoice> characterCombo = new JComboBox<>();
    private final JTextField startedField = new JTextField(14);
    private final JTextField durationField = new JTextField(6);
    private final JComboBox<AbyssTier> tierCombo = new JComboBox<>(AbyssTier.values());
    private final JComboBox<AbyssWeather> weatherCombo = new JComboBox<>(AbyssWeather.values());
    private final JComboBox<AbyssFleet> fleetCombo = new JComboBox<>(AbyssFleet.values());
    private final JTextField shipField = new JTextField(16);
    private final JCheckBox lostCheck = new JCheckBox("Ship lost");
    private final JTextField filamentField = new JTextField(12);
    private final JTextField lootField = new JTextField(12);
    private final JTextField notesField = new JTextField(40);
    private final JTextArea beforeArea = new JTextArea(8, 30);
    private final JTextArea afterArea = new JTextArea(8, 30);
    private final LootTableModel lootModel;
    private final JTable lootTable;
    private final JLabel lootStatus = new JLabel(" ");
    private final JLabel profitLabel = new JLabel(" ");
    private final Timer lootDebounce;
    private final AtomicInteger lootGeneration = new AtomicInteger();
    private final AtomicInteger filamentGeneration = new AtomicInteger();
    private final Set<Integer> removedHere = new HashSet<>();
    private List<AbyssalLoot> loot = List.of();
    private List<String> unknownNames = List.of();
    private int leftOut;
    private Long cargoPrefilledFor;
    private String loadedAfter = "";
    private boolean filling;

    AbyssRunDialog(Window owner, AppContext appContext, AbyssalRun run, Runnable onSaved) {
        super(owner, run == null ? "Add Abyss Run" : "Abyss Run - " + describe(run), ModalityType.MODELESS);
        this.appContext = appContext;
        this.original = run;
        this.onSaved = onSaved;
        this.lootModel = new LootTableModel(appContext.itemIconService);
        this.lootTable = new JTable(lootModel);
        this.lootDebounce = new Timer(400, e -> calculateLoot());
        lootDebounce.setRepeats(false);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        buildUi();
        fill();
        pack();
        setLocationRelativeTo(owner);
    }

    private static String describe(AbyssalRun run) {
        StringBuilder text = new StringBuilder(run.characterName());
        if (run.tier() != null) {
            text.append(", ").append(run.tier());
        }
        if (run.weather() != null) {
            text.append(" ").append(run.weather());
        }
        return text.toString();
    }

    private void buildUi() {
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(3, 0, 3, 8);
        addRow(form, c, 0, "Character:", characterCombo, "Started:", startedField);
        addRow(form, c, 1, "Tier:", tierCombo, "Weather:", weatherCombo);
        JPanel shipRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        shipRow.add(shipField);
        shipRow.add(javax.swing.Box.createHorizontalStrut(10));
        shipRow.add(lostCheck);
        addRow(form, c, 2, "Fleet:", fleetCombo, "Time (mm:ss):", durationField);
        addRow(form, c, 3, "Ship:", shipRow, "", new JLabel());
        addRow(form, c, 4, "Filament cost:", filamentField, "Loot value:", lootField);
        c.gridx = 0;
        c.gridy = 5;
        form.add(new JLabel("Notes:"), c);
        c.gridx = 1;
        c.gridwidth = 3;
        c.fill = GridBagConstraints.HORIZONTAL;
        form.add(notesField, c);
        c.gridwidth = 1;
        c.fill = GridBagConstraints.NONE;

        beforeArea.setLineWrap(false);
        afterArea.setLineWrap(false);
        JPanel cargo = new JPanel(new GridLayout(1, 2, 10, 0));
        cargo.add(cargoSection("Cargo before the run", beforeArea));
        cargo.add(cargoSection("Cargo after the run", afterArea));
        JLabel cargoHint = new JLabel("In EVE open the cargo hold, select all items (Ctrl+A), copy (Ctrl+C) "
                + "and paste here. Only what you gained is counted as loot.");
        cargoHint.setForeground(UIManager.getColor("Label.disabledForeground"));

        TableStyler.style(lootTable);
        lootTable.setRowHeight(Math.max(lootTable.getRowHeight(), ItemIconService.RENDER_SIZE + 6));
        JScrollPane lootScroll = new JScrollPane(lootTable);
        lootScroll.setPreferredSize(new Dimension(760, 170));

        installLootMenu();
        JLabel lootHint = new JLabel("Right-click an item that isn't loot, such as your own ammo");
        lootHint.setForeground(UIManager.getColor("Label.disabledForeground"));
        JButton ignoredButton = new JButton("Ignored Items...");
        ignoredButton.addActionListener(e -> manageIgnoredItems());
        JPanel lootTools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        lootTools.add(lootHint);
        lootTools.add(ignoredButton);
        JPanel lootHeader = new JPanel(new BorderLayout());
        lootHeader.add(sectionTitle("Loot"), BorderLayout.WEST);
        lootHeader.add(lootTools, BorderLayout.EAST);

        JPanel lootPanel = new JPanel(new BorderLayout(0, 4));
        lootPanel.add(lootHeader, BorderLayout.NORTH);
        lootPanel.add(lootScroll, BorderLayout.CENTER);
        lootPanel.add(lootStatus, BorderLayout.SOUTH);

        JPanel cargoPanel = new JPanel(new BorderLayout(0, 4));
        cargoPanel.add(cargo, BorderLayout.CENTER);
        cargoPanel.add(cargoHint, BorderLayout.SOUTH);

        JPanel middle = new JPanel(new BorderLayout(0, 10));
        middle.add(cargoPanel, BorderLayout.NORTH);
        middle.add(lootPanel, BorderLayout.CENTER);

        JButton saveButton = new JButton("Save", Icons.SAVE);
        saveButton.addActionListener(e -> save());
        JButton cancelButton = new JButton("Cancel", Icons.CANCEL);
        cancelButton.addActionListener(e -> dispose());
        profitLabel.setFont(profitLabel.getFont().deriveFont(Font.BOLD));
        JPanel buttons = new JPanel(new BorderLayout());
        buttons.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        buttons.add(profitLabel, BorderLayout.WEST);
        buttons.add(ButtonSizing.row(6, saveButton, cancelButton), BorderLayout.EAST);

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 6, 12));
        content.add(form, BorderLayout.NORTH);
        content.add(middle, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);

        DocumentListener cargoChanged = onChange(() -> {
            if (!filling) {
                lootDebounce.restart();
            }
        });
        beforeArea.getDocument().addDocumentListener(cargoChanged);
        afterArea.getDocument().addDocumentListener(cargoChanged);
        DocumentListener valuesChanged = onChange(this::updateProfit);
        lootField.getDocument().addDocumentListener(valuesChanged);
        filamentField.getDocument().addDocumentListener(valuesChanged);
        tierCombo.addActionListener(e -> lookUpFilamentCost());
        weatherCombo.addActionListener(e -> lookUpFilamentCost());
        fleetCombo.addActionListener(e -> lookUpFilamentCost());
        fleetCombo.setToolTipText("Every ship uses a filament of its own, so the cost counts them all");
        characterCombo.addActionListener(e -> prefillCargoBefore());
    }

    private void installLootMenu() {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem notLoot = new JMenuItem("Not Loot", Icons.REMOVE);
        notLoot.addActionListener(e -> removeSelectedLoot(false));
        JMenuItem alwaysIgnore = new JMenuItem("Always Ignore", Icons.CANCEL);
        alwaysIgnore.addActionListener(e -> removeSelectedLoot(true));
        menu.add(notLoot);
        menu.add(alwaysIgnore);
        lootTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                showMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                showMenu(e);
            }

            private void showMenu(MouseEvent e) {
                if (!e.isPopupTrigger()) {
                    return;
                }
                int row = lootTable.rowAtPoint(e.getPoint());
                if (row < 0) {
                    return;
                }
                if (!lootTable.isRowSelected(row)) {
                    lootTable.changeSelection(row, Math.max(0, lootTable.columnAtPoint(e.getPoint())), false, false);
                }
                List<AbyssalLoot> chosen = selectedLoot();
                alwaysIgnore.setText(chosen.size() == 1
                        ? "Always Ignore " + chosen.get(0).typeName()
                        : "Always Ignore These " + chosen.size() + " Items");
                menu.show(lootTable, e.getX(), e.getY());
            }
        });
        lootTable.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "notLoot");
        lootTable.getActionMap().put("notLoot", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                removeSelectedLoot(false);
            }
        });
    }

    private List<AbyssalLoot> selectedLoot() {
        return Arrays.stream(lootTable.getSelectedRows())
                .mapToObj(row -> loot.get(lootTable.convertRowIndexToModel(row)))
                .distinct()
                .toList();
    }

    private void removeSelectedLoot(boolean always) {
        List<AbyssalLoot> chosen = selectedLoot();
        if (chosen.isEmpty()) {
            return;
        }
        Set<Integer> ids = new HashSet<>();
        for (AbyssalLoot item : chosen) {
            ids.add(item.typeId());
            if (always) {
                appContext.abyssalRunDao.ignoreItem(item.typeId(), item.typeName());
            }
        }
        removedHere.addAll(ids);
        leftOut += chosen.size();
        showLoot(loot.stream().filter(item -> !ids.contains(item.typeId())).toList(), unknownNames, leftOut, true);
    }

    private void manageIgnoredItems() {
        if (AbyssIgnoredItemsDialog.showDialog(this, appContext) && !afterArea.getText().isBlank()) {
            calculateLoot();
        }
    }

    private static void addRow(JPanel form, GridBagConstraints c, int row, String firstLabel,
                               java.awt.Component first, String secondLabel, java.awt.Component second) {
        c.gridy = row;
        c.gridx = 0;
        form.add(new JLabel(firstLabel), c);
        c.gridx = 1;
        form.add(first, c);
        c.gridx = 2;
        c.insets = new Insets(3, 16, 3, 8);
        form.add(new JLabel(secondLabel), c);
        c.insets = new Insets(3, 0, 3, 8);
        c.gridx = 3;
        form.add(second, c);
    }

    private static JPanel cargoSection(String title, JTextArea area) {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.add(sectionTitle(title), BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(370, 150));
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private static JLabel sectionTitle(String title) {
        JLabel label = new JLabel(title);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        return label;
    }

    private static DocumentListener onChange(Runnable action) {
        return new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                action.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                action.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                action.run();
            }
        };
    }

    private void fill() {
        List<EveCharacter> characters = appContext.characterService.listCharactersMainFirst();
        for (EveCharacter character : characters) {
            characterCombo.addItem(new CharacterChoice(character.characterId(), character.characterName()));
        }
        if (original == null) {
            startedField.setText(STARTED_FORMAT.format(LocalDateTime.now()));
            appContext.settingsDao.getEnum(SettingsDao.ABYSS_TIER, AbyssTier.class).ifPresent(tierCombo::setSelectedItem);
            appContext.settingsDao.getEnum(SettingsDao.ABYSS_WEATHER, AbyssWeather.class)
                    .ifPresent(weatherCombo::setSelectedItem);
            appContext.settingsDao.getEnum(SettingsDao.ABYSS_FLEET, AbyssFleet.class).ifPresent(fleetCombo::setSelectedItem);
            lootField.setText(IskFormatter.formatPlain(0));
            prefillCargoBefore();
            lookUpFilamentCost();
            return;
        }
        for (int i = 0; i < characterCombo.getItemCount(); i++) {
            if (characterCombo.getItemAt(i).id() == original.characterId()) {
                characterCombo.setSelectedIndex(i);
            }
        }
        startedField.setText(STARTED_FORMAT.format(LocalDateTime.ofInstant(original.startedAt(),
                ZoneId.systemDefault())));
        durationField.setText(AbyssRunsTableModel.formatDuration(original.durationSeconds()));
        tierCombo.setSelectedItem(original.tier());
        weatherCombo.setSelectedItem(original.weather());
        fleetCombo.setSelectedItem(original.fleet());
        shipField.setText(original.shipName() == null ? "" : original.shipName());
        lostCheck.setSelected(!original.survived());
        filamentField.setText(original.filamentCost() == null ? "" : IskFormatter.formatPlain(original.filamentCost()));
        lootField.setText(IskFormatter.formatPlain(original.lootValue()));
        notesField.setText(original.notes() == null ? "" : original.notes());
        filamentGeneration.incrementAndGet();
        appContext.abyssalRunDao.findCargo(original.id()).ifPresent(cargo -> {
            filling = true;
            beforeArea.setText(cargo.before() == null ? "" : cargo.before());
            afterArea.setText(cargo.after() == null ? "" : cargo.after());
            beforeArea.setCaretPosition(0);
            afterArea.setCaretPosition(0);
            filling = false;
            loadedAfter = afterArea.getText();
            cargoPrefilledFor = original.characterId();
        });
        loadSavedLoot();
    }

    private void loadSavedLoot() {
        long runId = original.id();
        new SwingWorker<List<AbyssalLoot>, Void>() {
            @Override
            protected List<AbyssalLoot> doInBackground() {
                return appContext.abyssalRunDao.listLoot(runId);
            }

            @Override
            protected void done() {
                try {
                    List<AbyssalLoot> saved = get();
                    if (lootGeneration.get() == 0) {
                        showLoot(saved, List.of(), 0, false);
                    }
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Failed to load the loot of Abyssal run " + runId, e);
                }
            }
        }.execute();
    }

    private void prefillCargoBefore() {
        CharacterChoice character = (CharacterChoice) characterCombo.getSelectedItem();
        if (character == null || (original != null && (original.lootValue() != 0 || !loot.isEmpty()
                || !isLatestRun(original.characterId(), original.id())))) {
            return;
        }
        if (cargoPrefilledFor != null ? cargoPrefilledFor == character.id() : !beforeArea.getText().isBlank()) {
            return;
        }
        filling = true;
        beforeArea.setText(appContext.settingsDao.getOrDefault(SettingsDao.ABYSS_CARGO_PREFIX + character.id(), ""));
        beforeArea.setCaretPosition(0);
        filling = false;
        cargoPrefilledFor = character.id();
    }

    long runId() {
        return original == null ? 0 : original.id();
    }

    void setCargoAfter(String cargo) {
        afterArea.setText(cargo);
        afterArea.setCaretPosition(0);
    }

    private void lookUpFilamentCost() {
        AbyssTier tier = (AbyssTier) tierCombo.getSelectedItem();
        AbyssWeather weather = (AbyssWeather) weatherCombo.getSelectedItem();
        AbyssFleet fleet = (AbyssFleet) fleetCombo.getSelectedItem();
        int generation = filamentGeneration.incrementAndGet();
        new SwingWorker<Double, Void>() {
            @Override
            protected Double doInBackground() {
                return appContext.abyssLootService.filamentCost(tier, weather, fleet);
            }

            @Override
            protected void done() {
                if (generation != filamentGeneration.get()) {
                    return;
                }
                try {
                    Double cost = get();
                    filamentField.setText(cost == null ? "" : IskFormatter.formatPlain(cost));
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Failed to price the filament", e);
                }
            }
        }.execute();
    }

    private void calculateLoot() {
        String before = beforeArea.getText();
        String after = afterArea.getText();
        if (after.isBlank()) {
            lootStatus.setText("Paste the cargo after the run to count the loot.");
            return;
        }
        int generation = lootGeneration.incrementAndGet();
        lootStatus.setText("Pricing the loot...");
        new SwingWorker<AbyssLootService.LootResult, Void>() {
            @Override
            protected AbyssLootService.LootResult doInBackground() {
                return appContext.abyssLootService.calculate(before, after);
            }

            @Override
            protected void done() {
                if (generation != lootGeneration.get()) {
                    return;
                }
                try {
                    AbyssLootService.LootResult result = get();
                    List<AbyssalLoot> kept = result.items().stream()
                            .filter(item -> !removedHere.contains(item.typeId())).toList();
                    leftOut = result.ignored().size() + result.items().size() - kept.size();
                    showLoot(kept, result.unknownNames(), leftOut, true);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Failed to calculate the Abyssal loot", e);
                    lootStatus.setText("Couldn't calculate the loot - see the log.");
                }
            }
        }.execute();
    }

    private void showLoot(List<AbyssalLoot> items, List<String> unknown, int ignoredCount, boolean updateValue) {
        loot = items;
        unknownNames = unknown;
        lootModel.setItems(items);
        TableStyler.packColumns(lootTable);
        for (AbyssalLoot item : items) {
            appContext.itemIconService.loadAsync(item.typeId(), lootTable::repaint);
        }
        double total = items.stream().mapToDouble(AbyssalLoot::totalValue).sum();
        if (updateValue) {
            lootField.setText(IskFormatter.formatPlain(total));
        }
        long unpriced = items.stream().filter(item -> item.unitPrice() == null).count();
        StringBuilder status = new StringBuilder(items.isEmpty() ? "No loot."
                : items.size() + " items worth " + IskFormatter.format(total));
        if (unpriced > 0) {
            status.append(", ").append(unpriced).append(" without a market price");
        }
        if (ignoredCount > 0) {
            status.append(". ").append(ignoredCount).append(ignoredCount == 1 ? " item" : " items")
                    .append(" left out as not loot");
        }
        if (!unknown.isEmpty()) {
            status.append(". Not recognised: ").append(String.join(", ", unknown.stream().limit(5).toList()));
            if (unknown.size() > 5) {
                status.append(" and ").append(unknown.size() - 5).append(" more");
            }
        }
        lootStatus.setText(status.toString());
    }

    private void updateProfit() {
        Double lootValue = parseIsk(lootField.getText());
        Double filament = parseIsk(filamentField.getText());
        if (lootValue == null) {
            profitLabel.setText(" ");
            return;
        }
        double profit = lootValue - (filament == null ? 0 : filament);
        profitLabel.setText("Profit: " + IskFormatter.format(profit));
    }

    private void save() {
        CharacterChoice character = (CharacterChoice) characterCombo.getSelectedItem();
        if (character == null) {
            error("Add a character first.");
            return;
        }
        Instant started = parseStarted(startedField.getText());
        if (started == null) {
            error("Type the start as year-month-day hour:minute, for example 2026-09-28 18:30.");
            return;
        }
        Integer duration = parseDuration(durationField.getText());
        if (!durationField.getText().isBlank() && duration == null) {
            error("Type the time as minutes:seconds, for example 14:32.");
            return;
        }
        Double lootValue = lootField.getText().isBlank() ? Double.valueOf(0) : parseIsk(lootField.getText());
        Double filament = filamentField.getText().isBlank() ? null : parseIsk(filamentField.getText());
        if (lootValue == null || (!filamentField.getText().isBlank() && filament == null)) {
            error("Type ISK amounts as numbers, for example 12,500,000.");
            return;
        }
        String ship = shipField.getText().strip();
        String notes = notesField.getText().strip();
        boolean sameShip = original != null && ship.equals(original.shipName() == null ? "" : original.shipName());
        AbyssalRun run = new AbyssalRun(original == null ? 0 : original.id(), character.id(), character.name(),
                started, duration, (AbyssTier) tierCombo.getSelectedItem(),
                (AbyssWeather) weatherCombo.getSelectedItem(), (AbyssFleet) fleetCombo.getSelectedItem(),
                sameShip ? original.shipTypeId() : null,
                ship.isEmpty() ? null : ship, !lostCheck.isSelected(), lootValue, filament,
                notes.isEmpty() ? null : notes);
        String before = beforeArea.getText();
        String after = afterArea.getText();
        AbyssalCargo cargo = before.isBlank() && after.isBlank() ? null : new AbyssalCargo(before, after);
        try {
            long id = appContext.abyssalRunDao.save(run, loot, cargo);
            if (!after.isBlank() && !after.equals(loadedAfter) && isLatestRun(character.id(), id)) {
                appContext.settingsDao.set(SettingsDao.ABYSS_CARGO_PREFIX + character.id(), after);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to save the Abyssal run", e);
            error("Couldn't save the run: " + e.getMessage());
            return;
        }
        onSaved.run();
        dispose();
    }

    private boolean isLatestRun(long characterId, long runId) {
        return appContext.abyssalRunDao.listRuns().stream()
                .filter(run -> run.characterId() == characterId)
                .findFirst()
                .map(run -> run.id() == runId)
                .orElse(false);
    }

    private Instant parseStarted(String text) {
        if (original != null && text.strip().equals(STARTED_FORMAT.format(
                LocalDateTime.ofInstant(original.startedAt(), ZoneId.systemDefault())))) {
            return original.startedAt();
        }
        try {
            return LocalDateTime.parse(text.strip(), STARTED_FORMAT).atZone(ZoneId.systemDefault()).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    static Integer parseDuration(String text) {
        String value = text.strip();
        if (value.isEmpty()) {
            return null;
        }
        String[] parts = value.split(":");
        if (parts.length > 3) {
            return null;
        }
        try {
            int seconds = 0;
            for (String part : parts) {
                int number = Integer.parseInt(part.strip());
                if (number < 0) {
                    return null;
                }
                seconds = seconds * 60 + number;
            }
            if (parts.length == 1) {
                seconds *= 60;
            }
            return seconds;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Double parseIsk(String text) {
        String value = text.strip().toUpperCase(Locale.ROOT).replace("ISK", "").replace(",", "")
                .replace(" ", "").replace(" ", "");
        if (value.isEmpty()) {
            return null;
        }
        try {
            double number = Double.parseDouble(value);
            return Double.isFinite(number) && number >= 0 ? number : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void error(String message) {
        JOptionPane.showMessageDialog(this, message, getTitle(), JOptionPane.WARNING_MESSAGE);
    }

    private static final class LootTableModel extends AbstractTableModel {

        private static final String[] COLUMNS = {"", "Item", "Quantity", "Unit Price", "Total"};

        private final ItemIconService icons;
        private List<AbyssalLoot> items = List.of();

        LootTableModel(ItemIconService icons) {
            this.icons = icons;
        }

        void setItems(List<AbyssalLoot> items) {
            this.items = items;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return items.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 0 ? ImageIcon.class : String.class;
        }

        @Override
        public Object getValueAt(int row, int column) {
            AbyssalLoot item = items.get(row);
            return switch (column) {
                case 0 -> icons.iconOrPlaceholder(item.typeId());
                case 1 -> item.typeName();
                case 2 -> String.format(Locale.US, "%,d", item.quantity());
                case 3 -> item.unitPrice() == null ? "" : IskFormatter.format(item.unitPrice());
                case 4 -> item.unitPrice() == null ? "" : IskFormatter.format(item.totalValue());
                default -> "";
            };
        }
    }
}
