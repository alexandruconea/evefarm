package com.evefarm.ui;

import com.evefarm.AppContext;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.model.AbyssFleet;
import com.evefarm.model.AbyssTier;
import com.evefarm.model.AbyssWeather;
import com.evefarm.model.AbyssalCargo;
import com.evefarm.model.AbyssalRun;
import com.evefarm.model.EveCharacter;
import com.evefarm.service.AbyssCargoRouter;
import com.evefarm.service.AbyssLootService;
import com.evefarm.service.AbyssStats;
import com.evefarm.service.AbyssTrackerService;
import com.evefarm.service.AggroWatchService;
import com.evefarm.service.CargoParser;
import com.evefarm.util.IskFormatter;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class AbyssPanel extends JPanel {

    private static final Logger LOG = Logger.getLogger(AbyssPanel.class.getName());
    private static final String PANEL_KEY = "abyss";
    private static final long RUN_LIMIT_SECONDS = 20 * 60;
    private static final long WARNING_SECONDS = 5 * 60;
    private static final String GROUP_BY_TIER = "Tier";
    private static final String GROUP_BY_FILAMENT = "Filament";
    private static final String GROUP_BY_FLEET = "Fleet";
    private static final Color SURVIVED_ON_DARK = new Color(193, 212, 169);
    private static final Color SURVIVED_ON_LIGHT = new Color(0x3d, 0x7a, 0x2a);
    private static final Color LOST_FALLBACK = new Color(0xc0, 0x39, 0x2b);
    private static final DateTimeFormatter COPY_TIME = DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter AGGRO_TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final AppContext appContext;
    private final BiConsumer<String, String> notifier;
    private final JComboBox<String> characterCombo = new JComboBox<>();
    private final JComboBox<AbyssTier> tierCombo = new JComboBox<>(AbyssTier.values());
    private final JComboBox<AbyssWeather> weatherCombo = new JComboBox<>(AbyssWeather.values());
    private final JComboBox<AbyssFleet> fleetCombo = new JComboBox<>(AbyssFleet.values());
    private final JButton trackButton = new JButton();
    private final JCheckBox lootPromptCheck = new JCheckBox("Open the run window after each run");
    private final JButton pasteBeforeButton = new JButton("Paste Cargo Before", Icons.CLIPBOARD);
    private final JLabel cargoLabel = new JLabel(" ");
    private final JCheckBox aggroCheck = new JCheckBox("Say who gets aggro");
    private final JLabel aggroLabel = new JLabel(" ");
    private final AbyssCargoRouter cargoRouter = new AbyssCargoRouter();
    private final CargoClipboardWatcher clipboardWatcher = new CargoClipboardWatcher(this::clipboardChanged);
    private final JLabel timerLabel = new JLabel("--:--");
    private final JLabel statusLabel = new JLabel(" ");
    private final JButton addButton = new JButton("Add Run...", Icons.ADD);
    private final JButton editButton = new JButton("Edit...", Icons.DOCUMENT);
    private final JButton deleteButton = new JButton("Delete", Icons.REMOVE);
    private final JButton ignoredButton = new JButton("Ignored Items...", Icons.EYE);
    private final AbyssRunsTableModel tableModel = new AbyssRunsTableModel();
    private final TableCellRenderer resultRenderer = new ResultRenderer();
    private final JTable table = new JTable() {
        @Override
        public TableCellRenderer getCellRenderer(int row, int column) {
            String key = tableModel.columns().get(convertColumnIndexToModel(column)).key();
            return AbyssRunsTableModel.RESULT_COLUMN.equals(key) ? resultRenderer : super.getCellRenderer(row, column);
        }
    };
    private final JPanel filterBarContainer = new JPanel();
    private final JLabel countLabel = new JLabel("0 runs");
    private final JLabel overallLabel = new JLabel(" ");
    private final JComboBox<String> groupByCombo = new JComboBox<>(new String[]{GROUP_BY_TIER, GROUP_BY_FILAMENT, GROUP_BY_FLEET});
    private final StatsTableModel statsModel = new StatsTableModel();
    private final JTable statsTable = new JTable(statsModel);
    private final Timer clock = new Timer(1000, e -> updateTimer());
    private List<EveCharacter> characters = List.of();
    private String mainCharacterName;
    private AbyssTrackerService.Status trackerStatus;
    private AbyssRunDialog lootDialog;
    private boolean stoppingByUser;
    private Long beforeCopiedFor;
    private Instant beforeCopiedAt;
    private String cargoNote;
    private DataTablePanelSupport<AbyssalRun> support;

    public AbyssPanel(AppContext appContext, BiConsumer<String, String> notifier) {
        this.appContext = appContext;
        this.notifier = notifier;
        initComponents();
        postInit();
    }

    private void postInit() {
        characterCombo.setRenderer(MainCharacterMarks.comboRenderer(() -> mainCharacterName));
        characterCombo.addActionListener(e -> updateTrackerUi());
        AbyssRunDialog.selectSaved(tierCombo, AbyssTier.class, appContext.settingsDao, SettingsDao.ABYSS_TIER);
        AbyssRunDialog.selectSaved(weatherCombo, AbyssWeather.class, appContext.settingsDao,
                SettingsDao.ABYSS_WEATHER);
        AbyssRunDialog.selectSaved(fleetCombo, AbyssFleet.class, appContext.settingsDao, SettingsDao.ABYSS_FLEET);
        tierCombo.addActionListener(e -> filamentChanged());
        weatherCombo.addActionListener(e -> filamentChanged());
        fleetCombo.addActionListener(e -> filamentChanged());
        fleetCombo.setToolTipText("The ships that go in on one filament. Every ship uses a filament of its own.");
        lootPromptCheck.setSelected("true".equals(
                appContext.settingsDao.getOrDefault(SettingsDao.ABYSS_LOOT_PROMPT, "false")));
        lootPromptCheck.addActionListener(e -> appContext.settingsDao.set(SettingsDao.ABYSS_LOOT_PROMPT,
                String.valueOf(lootPromptCheck.isSelected())));
        trackButton.addActionListener(e -> toggleTracking());
        pasteBeforeButton.setToolTipText("Use what you copied from your cargo in EVE as the cargo before the next run");
        pasteBeforeButton.addActionListener(e -> pasteCargoBefore());
        aggroCheck.setToolTipText("Reads your characters' Gamelogs and says a character's name as soon as NPCs "
                + "start shooting at it");
        aggroCheck.setSelected("true".equals(
                appContext.settingsDao.getOrDefault(SettingsDao.ABYSS_AGGRO_VOICE, "false")));
        aggroCheck.addActionListener(e -> aggroToggled());
        appContext.aggroWatchService.addListener(alert -> {
            appContext.voiceService.say("Aggro on " + alert.characterName());
            SwingUtilities.invokeLater(() -> showAggro(alert));
        });
        addButton.addActionListener(e -> openRun(null));
        editButton.addActionListener(e -> editSelected());
        deleteButton.addActionListener(e -> deleteSelected());
        ignoredButton.setToolTipText("Items that are never counted as loot, such as your own ammo");
        ignoredButton.addActionListener(e -> AbyssIgnoredItemsDialog.showDialog(SwingUtilities.getWindowAncestor(this),
                appContext));
        groupByCombo.addActionListener(e -> refreshStats());

        support = new DataTablePanelSupport<>(appContext, PANEL_KEY, tableModel, table, filterBarContainer,
                countLabel, "runs", appContext.abyssalRunDao::listRuns);
        support.setOnRowsShown(this::refreshStats);
        support.init();
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e) && table.rowAtPoint(e.getPoint()) >= 0) {
                    editSelected();
                }
            }
        });
        TableStyler.style(statsTable);

        trackerStatus = appContext.abyssTrackerService.status();
        appContext.abyssTrackerService.addListener(event -> SwingUtilities.invokeLater(() -> onTrackerEvent(event)));
        reloadCharacters();
        clock.start();
        if (aggroCheck.isSelected()) {
            startAggroWatch();
        } else {
            aggroLabel.setText("Off");
        }
    }

    private void aggroToggled() {
        appContext.settingsDao.set(SettingsDao.ABYSS_AGGRO_VOICE, String.valueOf(aggroCheck.isSelected()));
        if (aggroCheck.isSelected()) {
            startAggroWatch();
        } else {
            appContext.aggroWatchService.stop();
            aggroLabel.setText("Off");
        }
    }

    private void startAggroWatch() {
        if (!appContext.killService.hasGameLogDirectory()) {
            aggroLabel.setText("EVE's Gamelogs folder wasn't found - set it in Options > Settings");
            return;
        }
        appContext.aggroWatchService.start();
        aggroLabel.setText("Listening for NPCs shooting at your characters");
    }

    private void showAggro(AggroWatchService.Alert alert) {
        if (aggroCheck.isSelected()) {
            aggroLabel.setText("Last: " + alert.characterName() + ", from " + alert.attacker() + " at "
                    + AGGRO_TIME.format(alert.at()));
        }
    }

    public void onShown() {
        support.reload();
    }

    public void refreshCharacterFilter() {
        reloadCharacters();
        support.reload();
    }

    private void reloadCharacters() {
        characters = appContext.characterService.listCharactersMainFirst();
        Long mainId = appContext.characterService.mainCharacterId().orElse(null);
        mainCharacterName = characters.stream()
                .filter(character -> mainId != null && character.characterId() == mainId)
                .map(EveCharacter::characterName)
                .findFirst().orElse(null);
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        characters.forEach(character -> model.addElement(character.characterName()));
        characterCombo.setModel(model);
        if (trackerStatus != null && trackerStatus.phase() != AbyssTrackerService.Phase.STOPPED
                && model.getIndexOf(trackerStatus.characterName()) >= 0) {
            characterCombo.setSelectedItem(trackerStatus.characterName());
        } else if (model.getSize() > 0) {
            characterCombo.setSelectedIndex(0);
        }
        updateTrackerUi();
    }

    private void filamentChanged() {
        AbyssTier tier = (AbyssTier) tierCombo.getSelectedItem();
        AbyssWeather weather = (AbyssWeather) weatherCombo.getSelectedItem();
        if (tier != null) {
            appContext.settingsDao.set(SettingsDao.ABYSS_TIER, tier.name());
        }
        if (weather != null) {
            appContext.settingsDao.set(SettingsDao.ABYSS_WEATHER, weather.name());
        }
        AbyssFleet fleet = (AbyssFleet) fleetCombo.getSelectedItem();
        if (fleet != null) {
            appContext.settingsDao.set(SettingsDao.ABYSS_FLEET, fleet.name());
        }
        appContext.abyssTrackerService.changeFilament(tier, weather, fleet);
    }

    private EveCharacter selectedCharacter() {
        String name = (String) characterCombo.getSelectedItem();
        return characters.stream().filter(character -> character.characterName().equals(name)).findFirst()
                .orElse(null);
    }

    private boolean tracking() {
        return trackerStatus != null && trackerStatus.phase() != AbyssTrackerService.Phase.STOPPED;
    }

    private void toggleTracking() {
        if (tracking()) {
            stoppingByUser = appContext.abyssTrackerService.status().phase() != AbyssTrackerService.Phase.STOPPED;
            appContext.abyssTrackerService.stop();
            return;
        }
        EveCharacter character = selectedCharacter();
        if (character == null) {
            JOptionPane.showMessageDialog(this, "Add a character first from File > Characters.",
                    "Abyss Tracking", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (!AbyssTrackerService.canTrack(character)) {
            JOptionPane.showMessageDialog(this,
                    character.characterName() + " has to log in again so EVE Farm may read its location and ship.\n\n"
                            + "Open File > Characters, click Add Character and log in with "
                            + character.characterName() + ".\nIts data stays as it is. Then start tracking again.",
                    "Abyss Tracking", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        try {
            appContext.abyssTrackerService.start(character, (AbyssTier) tierCombo.getSelectedItem(),
                    (AbyssWeather) weatherCombo.getSelectedItem(), (AbyssFleet) fleetCombo.getSelectedItem());
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Couldn't start Abyss tracking", e);
            JOptionPane.showMessageDialog(this, e.getMessage(), "Abyss Tracking", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void onTrackerEvent(AbyssTrackerService.Event event) {
        trackerStatus = event.status();
        updateTrackerUi();
        AbyssTrackerService.Status status = event.status();
        switch (event.type()) {
            case STARTED -> {
                stoppingByUser = false;
                cargoRouter.reset();
                cargoNote = null;
                clipboardWatcher.start();
            }
            case ENTERED_ABYSS -> {
                if (status.fleet() != null && status.fleet() != fleetCombo.getSelectedItem()) {
                    fleetCombo.setSelectedItem(status.fleet());
                }
                notifier.accept("Entered the Abyss",
                        status.characterName() + " entered the Abyss. The 20 minute timer is running.");
            }
            case LEFT_ABYSS -> runFinished(event.run());
            case STOPPED -> {
                clipboardWatcher.stop();
                cargoRouter.reset();
                if (stoppingByUser) {
                    stoppingByUser = false;
                } else if (status.message() != null) {
                    notifier.accept("Abyss tracking stopped", status.message());
                }
            }
            default -> {
            }
        }
    }

    private void runFinished(AbyssalRun run) {
        support.reload();
        if (run == null) {
            return;
        }
        String time = AbyssRunsTableModel.formatDuration(run.durationSeconds());
        notifier.accept(run.survived() ? "Abyss run saved" : "Abyss run saved - ship lost",
                run.characterName() + " left the Abyss after " + time
                        + ". Copy your cargo in EVE (Ctrl+A, Ctrl+C) to count the loot.");
        if (lootPromptCheck.isSelected() && (lootDialog == null || !lootDialog.isDisplayable())) {
            lootDialog = new AbyssRunDialog(SwingUtilities.getWindowAncestor(this), appContext, run, support::reload);
            lootDialog.setAutoRequestFocus(false);
            lootDialog.setVisible(true);
        }
        cargoNote = "Waiting for the cargo after the run - copy it in EVE (Ctrl+A, Ctrl+C)";
        cargoRouter.runFinished(run, Instant.now()).ifPresent(this::useCargoAfter);
        updateCargoLabel();
    }

    private void clipboardChanged(String text) {
        if (!tracking() || !CargoParser.looksLikeEveCopy(text)) {
            return;
        }
        Instant copiedAt = Instant.now();
        new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() {
                return appContext.abyssLootService.recognizedItems(text);
            }

            @Override
            protected void done() {
                try {
                    if (get() > 0) {
                        cargoCopied(text, copiedAt);
                    }
                } catch (Exception e) {
                    LOG.log(Level.FINE, "Couldn't read the copied cargo", e);
                }
            }
        }.execute();
    }

    private void cargoCopied(String cargo, Instant copiedAt) {
        if (!tracking()) {
            return;
        }
        switch (cargoRouter.copied(trackerStatus.phase(), cargo, copiedAt)) {
            case AbyssCargoRouter.Before before -> {
                saveCargoBefore(trackerStatus.characterId(), before.cargo(), copiedAt);
                cargoNote = null;
            }
            case AbyssCargoRouter.After after -> useCargoAfter(after);
            case AbyssCargoRouter.Held _ -> cargoNote = "Cargo copied in the Abyss - it counts as the cargo after the run";
            case AbyssCargoRouter.Ignored _ -> {
            }
        }
        updateCargoLabel();
    }

    private void saveCargoBefore(long characterId, String cargo, Instant copiedAt) {
        appContext.settingsDao.set(SettingsDao.ABYSS_CARGO_PREFIX + characterId, cargo);
        beforeCopiedFor = characterId;
        beforeCopiedAt = copiedAt;
    }

    private void useCargoAfter(AbyssCargoRouter.After after) {
        AbyssalRun run = after.run();
        String before = appContext.settingsDao.getOrDefault(SettingsDao.ABYSS_CARGO_PREFIX + run.characterId(), "");
        saveCargoBefore(run.characterId(), after.cargo(), Instant.now());
        if (lootDialog != null && lootDialog.isDisplayable() && lootDialog.runId() == run.id()) {
            lootDialog.setCargoAfter(after.cargo());
            cargoNote = "Cargo after the run copied into the run window";
            return;
        }
        cargoNote = "Counting the loot of the last run...";
        countLoot(run, before, after.cargo());
    }

    private void countLoot(AbyssalRun run, String before, String after) {
        new SwingWorker<AbyssalRun, Void>() {
            private AbyssLootService.LootResult result;

            @Override
            protected AbyssalRun doInBackground() {
                result = appContext.abyssLootService.calculate(before, after);
                AbyssalRun current = appContext.abyssalRunDao.listRuns().stream()
                        .filter(saved -> saved.id() == run.id()).findFirst().orElse(null);
                if (current == null) {
                    return null;
                }
                AbyssalRun counted = current.withLootValue(result.totalValue());
                appContext.abyssalRunDao.save(counted, result.items(), new AbyssalCargo(before, after));
                return counted;
            }

            @Override
            protected void done() {
                try {
                    AbyssalRun counted = get();
                    if (counted != null) {
                        cargoNote = "Loot of the last run: " + compact(counted.lootValue())
                                + (result.ignored().isEmpty() ? ""
                                : ", " + result.ignored().size() + " ignored items left out")
                                + (result.unknownNames().isEmpty() ? ""
                                : " (" + result.unknownNames().size() + " items not recognised)");
                        notifier.accept("Abyss loot counted", counted.characterName() + ": "
                                + compact(counted.lootValue()) + " of loot, profit " + compact(counted.profit()) + ".");
                        support.reload();
                    } else {
                        cargoNote = null;
                    }
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Failed to count the loot of Abyssal run " + run.id(), e);
                    cargoNote = "Couldn't count the loot - edit the run to paste the cargo";
                }
                updateCargoLabel();
            }
        }.execute();
    }

    private void pasteCargoBefore() {
        EveCharacter character = cargoCharacter();
        if (character == null) {
            return;
        }
        String text = CargoClipboardWatcher.readSystemClipboard();
        clipboardWatcher.markSeen(text);
        if (text == null || text.isBlank()) {
            showNoCargo();
            return;
        }
        new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() {
                return appContext.abyssLootService.recognizedItems(text);
            }

            @Override
            protected void done() {
                try {
                    if (get() == 0) {
                        showNoCargo();
                        return;
                    }
                    saveCargoBefore(character.characterId(), text, Instant.now());
                    cargoNote = null;
                    updateCargoLabel();
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Couldn't read the pasted cargo", e);
                }
            }
        }.execute();
    }

    private void showNoCargo() {
        JOptionPane.showMessageDialog(this,
                "The clipboard doesn't hold any EVE items.\n\nIn EVE open your cargo hold, press Ctrl+A and Ctrl+C, "
                        + "then click Paste Cargo Before again.",
                "Paste Cargo Before", JOptionPane.INFORMATION_MESSAGE);
    }

    private EveCharacter cargoCharacter() {
        if (tracking() && trackerStatus.characterId() != null) {
            long id = trackerStatus.characterId();
            return characters.stream().filter(character -> character.characterId() == id).findFirst()
                    .orElse(selectedCharacter());
        }
        return selectedCharacter();
    }

    private void updateCargoLabel() {
        EveCharacter character = cargoCharacter();
        if (character == null) {
            cargoLabel.setText(" ");
            return;
        }
        String cargo = appContext.settingsDao.getOrDefault(SettingsDao.ABYSS_CARGO_PREFIX + character.characterId(), "");
        int items = CargoParser.parse(cargo).size();
        String before;
        if (items == 0) {
            before = "Before the run: none yet - in EVE open your cargo and press Ctrl+A, Ctrl+C";
        } else if (beforeCopiedAt != null && beforeCopiedFor != null && beforeCopiedFor == character.characterId()) {
            before = "Before the run: " + items + (items == 1 ? " item" : " items") + ", copied at "
                    + COPY_TIME.format(beforeCopiedAt);
        } else {
            before = "Before the run: " + items + (items == 1 ? " item" : " items") + ", saved earlier";
        }
        cargoLabel.setText(cargoNote == null ? before : before + "   |   " + cargoNote);
    }

    private void updateTrackerUi() {
        boolean tracking = tracking();
        trackButton.setText(tracking ? "Stop Tracking" : "Start Tracking");
        trackButton.setIcon(tracking ? Icons.CANCEL : Icons.PLAY);
        characterCombo.setEnabled(!tracking);
        if (tracking && trackerStatus.characterName() != null
                && !trackerStatus.characterName().equals(characterCombo.getSelectedItem())) {
            characterCombo.setSelectedItem(trackerStatus.characterName());
        }
        String message = trackerStatus == null ? null : trackerStatus.message();
        if (!tracking) {
            EveCharacter character = selectedCharacter();
            if (character != null && !AbyssTrackerService.canTrack(character)) {
                message = character.characterName() + " has to log in again (File > Characters) before tracking.";
            } else if (message == null) {
                message = "Start tracking, then take a filament in EVE. The timer starts when you enter the Abyss.";
            }
        }
        statusLabel.setText(message == null ? " " : message);
        updateTimer();
        updateCargoLabel();
    }

    private void updateTimer() {
        if (trackerStatus == null || trackerStatus.phase() != AbyssTrackerService.Phase.IN_ABYSS
                || trackerStatus.enteredAt() == null) {
            timerLabel.setText("--:--");
            timerLabel.setForeground(UIManager.getColor("Label.foreground"));
            return;
        }
        long elapsed = Math.max(0, Duration.between(trackerStatus.enteredAt(), Instant.now()).toSeconds());
        long left = Math.max(0, RUN_LIMIT_SECONDS - elapsed);
        timerLabel.setText(AbyssRunsTableModel.formatDuration(elapsed) + "   (" + AbyssRunsTableModel.formatDuration(left)
                + " left)");
        Color warning = UIManager.getColor("Actions.Red");
        timerLabel.setForeground(left <= WARNING_SECONDS
                ? (warning != null ? warning : new Color(0xDB5860))
                : UIManager.getColor("Label.foreground"));
    }

    private AbyssalRun selectedRun() {
        int row = table.getSelectedRow();
        return row < 0 ? null : tableModel.rowAt(table.convertRowIndexToModel(row));
    }

    private void openRun(AbyssalRun run) {
        new AbyssRunDialog(SwingUtilities.getWindowAncestor(this), appContext, run, support::reload).setVisible(true);
    }

    private void editSelected() {
        AbyssalRun run = selectedRun();
        if (run == null) {
            JOptionPane.showMessageDialog(this, "Select a run first.", "Edit Run", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        openRun(run);
    }

    private void deleteSelected() {
        AbyssalRun run = selectedRun();
        if (run == null) {
            JOptionPane.showMessageDialog(this, "Select a run first.", "Delete Run", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                "Delete the " + AbyssRunsTableModel.formatDuration(run.durationSeconds()) + " run of "
                        + run.characterName() + " from " + com.evefarm.util.DateUtil.format(run.startedAt()) + "?",
                "Delete Run", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            appContext.abyssalRunDao.delete(run.id());
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Failed to delete Abyssal run " + run.id(), e);
            JOptionPane.showMessageDialog(this, "Couldn't delete the run: " + e.getMessage(), "Delete Run",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        support.reload();
    }

    private void refreshStats() {
        List<AbyssalRun> runs = support.visibleRows();
        AbyssStats.Summary overall = AbyssStats.overall(runs);
        if (overall.runs() == 0) {
            overallLabel.setText("No runs yet.");
        } else {
            overallLabel.setText(overall.runs() + (overall.runs() == 1 ? " run" : " runs")
                    + (overall.deaths() == 0 ? "" : ", " + overall.deaths() + " lost")
                    + "   |   Profit " + compact(overall.profit())
                    + "   |   Average " + compact(overall.averageProfit()) + " per run"
                    + (overall.averageSeconds() == null ? ""
                    : " in " + AbyssRunsTableModel.formatDuration(Math.round(overall.averageSeconds())))
                    + (overall.iskPerHour() == null ? "" : "   |   " + compact(overall.iskPerHour()) + " ISK/h"));
        }
        Object groupBy = groupByCombo.getSelectedItem();
        statsModel.setSummaries(GROUP_BY_FILAMENT.equals(groupBy) ? AbyssStats.byFilament(runs)
                : GROUP_BY_FLEET.equals(groupBy) ? AbyssStats.byFleet(runs) : AbyssStats.byTier(runs));
        TableStyler.packColumns(statsTable);
    }

    private static String compact(double isk) {
        return new CompactIskNumberFormat(2).format(isk) + " ISK";
    }

    private static void addStatusRow(JPanel panel, GridBagConstraints c, int row, String title,
                                     java.awt.Component control, JLabel text) {
        JLabel heading = new JLabel(title);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD));
        heading.setForeground(UIManager.getColor("Label.disabledForeground"));
        c.gridy = row;
        c.gridx = 0;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        panel.add(heading, c);
        c.gridx = 1;
        panel.add(control, c);
        c.gridx = 2;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(text, c);
    }

    private void initComponents() {
        setLayout(new BorderLayout());

        timerLabel.setFont(timerLabel.getFont().deriveFont(Font.BOLD, timerLabel.getFont().getSize2D() * 1.8f));
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        controls.add(new JLabel("Character:"));
        characterCombo.setPreferredSize(new Dimension(200, characterCombo.getPreferredSize().height));
        controls.add(characterCombo);
        controls.add(javax.swing.Box.createHorizontalStrut(8));
        controls.add(new JLabel("Filament:"));
        controls.add(tierCombo);
        controls.add(weatherCombo);
        controls.add(javax.swing.Box.createHorizontalStrut(8));
        controls.add(new JLabel("Fleet:"));
        controls.add(fleetCombo);
        controls.add(javax.swing.Box.createHorizontalStrut(8));
        controls.add(trackButton);
        controls.add(javax.swing.Box.createHorizontalStrut(8));
        controls.add(lootPromptCheck);

        JPanel statusRows = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(3, 0, 3, 16);
        addStatusRow(statusRows, c, 0, "Run", timerLabel, statusLabel);
        addStatusRow(statusRows, c, 1, "Cargo", pasteBeforeButton, cargoLabel);
        addStatusRow(statusRows, c, 2, "Aggro voice", aggroCheck, aggroLabel);

        JPanel tracker = new JPanel(new BorderLayout(0, 8));
        Color separator = UIManager.getColor("Separator.foreground");
        tracker.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, separator != null ? separator : Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(8, 8, 10, 8)));
        tracker.add(controls, BorderLayout.NORTH);
        tracker.add(statusRows, BorderLayout.CENTER);
        add(tracker, BorderLayout.NORTH);

        JPanel runButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        runButtons.add(ButtonSizing.row(6, addButton, editButton, deleteButton));
        runButtons.add(javax.swing.Box.createHorizontalStrut(6));
        runButtons.add(ignoredButton);
        JPanel runsTop = new JPanel(new BorderLayout(0, 6));
        runsTop.add(runButtons, BorderLayout.NORTH);
        runsTop.add(filterBarContainer, BorderLayout.CENTER);
        JPanel runsPanel = new JPanel(new BorderLayout());
        runsPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        runsPanel.add(runsTop, BorderLayout.NORTH);
        runsPanel.add(new JScrollPane(table), BorderLayout.CENTER);
        runsPanel.add(countLabel, BorderLayout.SOUTH);

        JPanel statsHeader = new JPanel(new BorderLayout(12, 0));
        JLabel statsTitle = new JLabel("Statistics");
        statsTitle.setFont(statsTitle.getFont().deriveFont(Font.BOLD));
        JPanel groupBy = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        groupBy.add(new JLabel("Group by:"));
        groupBy.add(groupByCombo);
        statsHeader.add(statsTitle, BorderLayout.WEST);
        statsHeader.add(overallLabel, BorderLayout.CENTER);
        statsHeader.add(groupBy, BorderLayout.EAST);
        JPanel statsPanel = new JPanel(new BorderLayout(0, 6));
        statsPanel.setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 8));
        statsPanel.add(statsHeader, BorderLayout.NORTH);
        JScrollPane statsScroll = new JScrollPane(statsTable);
        statsScroll.setPreferredSize(new Dimension(600, 150));
        statsPanel.add(statsScroll, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, runsPanel, statsPanel);
        split.setResizeWeight(0.7);
        split.setBorder(BorderFactory.createEmptyBorder());
        add(split, BorderLayout.CENTER);
    }

    private static final class ResultRenderer extends DefaultTableCellRenderer {

        ResultRenderer() {
            putClientProperty("html.disable", Boolean.TRUE);
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
            if (!isSelected) {
                setBackground(row % 2 == 0 ? t.getBackground() : TableStyler.stripeColor(t));
                setForeground(resultColor(t, value));
            }
            return this;
        }
    }

    static Color resultColor(JTable table, Object value) {
        if (AbyssRunsTableModel.SURVIVED.equals(value)) {
            return com.formdev.flatlaf.FlatLaf.isLafDark() ? SURVIVED_ON_DARK : SURVIVED_ON_LIGHT;
        }
        if (value != null && !String.valueOf(value).isBlank()) {
            Color red = UIManager.getColor("Actions.Red");
            return red != null ? red : LOST_FALLBACK;
        }
        return table.getForeground();
    }

    private static final class StatsTableModel extends AbstractTableModel {

        private static final String[] COLUMNS = {"Group", "Runs", "Lost", "Loot", "Filaments", "Profit",
                "Avg Profit", "Avg Time", "ISK/h"};

        private List<AbyssStats.Summary> summaries = List.of();

        void setSummaries(List<AbyssStats.Summary> summaries) {
            this.summaries = summaries;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return summaries.size();
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
            return column == 1 || column == 2 ? Integer.class : String.class;
        }

        @Override
        public Object getValueAt(int row, int column) {
            AbyssStats.Summary summary = summaries.get(row);
            return switch (column) {
                case 0 -> summary.label();
                case 1 -> summary.runs();
                case 2 -> summary.deaths();
                case 3 -> IskFormatter.format(summary.loot());
                case 4 -> IskFormatter.format(summary.filamentCost());
                case 5 -> IskFormatter.format(summary.profit());
                case 6 -> IskFormatter.format(summary.averageProfit());
                case 7 -> summary.averageSeconds() == null ? ""
                        : AbyssRunsTableModel.formatDuration(Math.round(summary.averageSeconds()));
                case 8 -> summary.iskPerHour() == null ? "" : IskFormatter.format(summary.iskPerHour());
                default -> "";
            };
        }
    }
}
