package com.evefarm.ui;

import com.evefarm.AppContext;
import com.evefarm.model.EveCharacter;
import com.evefarm.service.UpdateCategories;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class UpdateDialog extends JDialog {

    private static final Logger LOG = Logger.getLogger(UpdateDialog.class.getName());

    private static final Duration ASSETS_COOLDOWN = Duration.ofHours(1);
    private static final Duration MARKET_ORDERS_COOLDOWN = Duration.ofMinutes(20);
    private static final Duration JOURNAL_COOLDOWN = Duration.ofHours(1);
    private static final Duration TRANSACTIONS_COOLDOWN = Duration.ofHours(1);
    private static final Duration CONTRACTS_COOLDOWN = Duration.ofHours(1);
    private static final Duration INDUSTRY_JOBS_COOLDOWN = Duration.ofHours(1);
    private static final Duration TRACKER_COOLDOWN = Duration.ofHours(1);
    private static final Duration MARKET_PRICES_COOLDOWN = Duration.ofHours(1);
    private static final Duration LOYALTY_POINTS_COOLDOWN = Duration.ofHours(1);
    private static final Duration KILLS_COOLDOWN = Duration.ofHours(1);

    private static final Dimension NOW_BUTTON_SIZE = new Dimension(100, 26);
    private static final Dimension BOTTOM_BUTTON_SIZE = new Dimension(130, 28);

    private record Category(String key, JCheckBox box, JButton nowButton,
                             Duration cooldown, Consumer<List<String>> action) {

        String label() {
            String text = box.getText();
            int detail = text.indexOf(" (");
            return detail < 0 ? text : text.substring(0, detail);
        }
    }

    private record RunResult(List<String> refreshedKeys, List<String> failures) {
    }

    private final AppContext appContext;
    private final Runnable onCompleted;
    private final List<Category> categories = new ArrayList<>();
    private final Timer cooldownTimer;
    private final AtomicBoolean cooldownRefreshInFlight = new AtomicBoolean(false);

    private final JCheckBox allBox = new JCheckBox("All");
    private final JRadioButton firstCharacterRadio = new JRadioButton("First Character");
    private final JRadioButton allCharactersRadio = new JRadioButton("All Characters", true);

    private final JCheckBox assetsBox = new JCheckBox("Assets", true);
    private final JCheckBox marketOrdersBox = new JCheckBox("Market Orders", true);
    private final JCheckBox journalBox = new JCheckBox("Journal", true);
    private final JCheckBox transactionsBox = new JCheckBox("Transactions", true);
    private final JCheckBox contractsBox = new JCheckBox("Contracts", true);
    private final JCheckBox industryJobsBox = new JCheckBox("Industry Jobs", true);
    private final JCheckBox trackerBox = new JCheckBox("Tracker Snapshot (Wallet, Net Worth, Skill Points...)", true);
    private final JCheckBox marketPricesBox = new JCheckBox("Market Prices", true);
    private final JCheckBox loyaltyPointsBox = new JCheckBox("Loyalty Points", true);
    private final JCheckBox killsBox = new JCheckBox("NPC Kills", true);

    private final JButton updateButton = new JButton("Update", Icons.REFRESH);

    public UpdateDialog(Window owner, AppContext appContext, Runnable onCompleted) {
        super(owner, "Update", ModalityType.APPLICATION_MODAL);
        this.appContext = appContext;
        this.onCompleted = onCompleted;
        buildUi();
        refreshCooldownStates();
        cooldownTimer = new Timer(1000, e -> refreshCooldownStates());
        cooldownTimer.start();
        pack();
        setLocationRelativeTo(owner);
    }

    private void buildUi() {
        ButtonGroup scopeGroup = new ButtonGroup();
        scopeGroup.add(firstCharacterRadio);
        scopeGroup.add(allCharactersRadio);

        JPanel topRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topRow.add(allBox);
        topRow.add(firstCharacterRadio);
        topRow.add(allCharactersRadio);

        JPanel rows = new JPanel();
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        rows.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        rows.add(row(UpdateCategories.ASSETS, assetsBox, ASSETS_COOLDOWN, this::runAssets));
        rows.add(row(UpdateCategories.MARKET_ORDERS, marketOrdersBox, MARKET_ORDERS_COOLDOWN, this::runMarketOrders));
        rows.add(row(UpdateCategories.JOURNAL, journalBox, JOURNAL_COOLDOWN, this::runJournal));
        rows.add(row(UpdateCategories.TRANSACTIONS, transactionsBox, TRANSACTIONS_COOLDOWN, this::runTransactions));
        rows.add(row(UpdateCategories.CONTRACTS, contractsBox, CONTRACTS_COOLDOWN, this::runContracts));
        rows.add(row(UpdateCategories.INDUSTRY_JOBS, industryJobsBox, INDUSTRY_JOBS_COOLDOWN, this::runIndustryJobs));
        rows.add(row(UpdateCategories.TRACKER, trackerBox, TRACKER_COOLDOWN, this::runTracker));
        rows.add(row(UpdateCategories.MARKET_PRICES, marketPricesBox, MARKET_PRICES_COOLDOWN, this::runMarketPrices));
        rows.add(row(UpdateCategories.LOYALTY_POINTS, loyaltyPointsBox, LOYALTY_POINTS_COOLDOWN, this::runLoyaltyPoints));
        rows.add(row(UpdateCategories.KILLS, killsBox, KILLS_COOLDOWN, this::runKills));

        for (String label : new String[]{
                "Blueprints", "Skills", "NPC Standing", "Mining"}) {
            rows.add(disabledRow(label));
        }

        allBox.addActionListener(e -> {
            boolean selected = allBox.isSelected();
            for (Category category : categories) {
                if (category.box().isEnabled()) {
                    category.box().setSelected(selected);
                }
            }
        });

        fixSize(updateButton, BOTTOM_BUTTON_SIZE);
        updateButton.setDisabledIcon(Icons.disabled(Icons.REFRESH));
        updateButton.addActionListener(e -> runUpdate());
        JButton cancelButton = new JButton("Cancel", Icons.CANCEL);
        fixSize(cancelButton, BOTTOM_BUTTON_SIZE);
        cancelButton.addActionListener(e -> dispose());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(updateButton);
        buttonPanel.add(cancelButton);

        JScrollPane scrollPane = new JScrollPane(rows);
        scrollPane.setPreferredSize(new Dimension(440, 300));

        setLayout(new BorderLayout());
        add(topRow, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private JPanel row(String key, JCheckBox box, Duration cooldown, Consumer<List<String>> action) {
        JButton nowButton = new JButton("Now", Icons.PLAY);
        fixSize(nowButton, NOW_BUTTON_SIZE);
        nowButton.setDisabledIcon(Icons.disabled(Icons.PLAY));
        Category category = new Category(key, box, nowButton, cooldown, action);
        nowButton.addActionListener(e -> runNow(category));

        categories.add(category);

        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        buttonsPanel.add(nowButton);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(box, BorderLayout.CENTER);
        panel.add(buttonsPanel, BorderLayout.EAST);
        return panel;
    }

    private static void fixSize(JButton button, Dimension size) {
        button.setPreferredSize(size);
        button.setMinimumSize(size);
        button.setMaximumSize(size);
    }

    private JPanel disabledRow(String label) {
        JPanel panel = new JPanel(new BorderLayout());
        JCheckBox box = new JCheckBox(label + " (not yet supported)");
        box.setEnabled(false);
        panel.add(box, BorderLayout.CENTER);
        return panel;
    }

    private void refreshCooldownStates() {
        if (!cooldownRefreshInFlight.compareAndSet(false, true)) {
            return;
        }
        new SwingWorker<Map<String, Optional<Instant>>, Void>() {
            @Override
            protected Map<String, Optional<Instant>> doInBackground() {
                Map<String, Optional<Instant>> result = new LinkedHashMap<>();
                for (Category category : categories) {
                    result.put(category.key(), appContext.updateCooldownDao.findLastRefreshed(category.key()));
                }
                return result;
            }

            @Override
            protected void done() {
                cooldownRefreshInFlight.set(false);
                try {
                    applyCooldownStates(get());
                } catch (Exception ignored) {
                }
            }
        }.execute();
    }

    private void applyCooldownStates(Map<String, Optional<Instant>> lastRefreshedByCategory) {
        Instant now = Instant.now();
        boolean anyEnabled = false;
        for (Category category : categories) {
            Optional<Instant> last = lastRefreshedByCategory.get(category.key());
            boolean ready = last == null || last.isEmpty() || now.isAfter(last.get().plus(category.cooldown()));
            category.box().setEnabled(ready);
            category.nowButton().setEnabled(ready);
            category.nowButton().setText(ready ? "Now"
                    : formatRemaining(Duration.between(now, last.get().plus(category.cooldown()))));
            anyEnabled |= ready;
        }
        allBox.setEnabled(anyEnabled);
    }

    private static String formatRemaining(Duration duration) {
        long totalSeconds = Math.max(0, duration.getSeconds());
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format("%dh %02dm", hours, minutes);
        }
        if (minutes > 0) {
            return String.format("%dm %02ds", minutes, seconds);
        }
        return String.format("%ds", seconds);
    }

    @Override
    public void dispose() {
        cooldownTimer.stop();
        super.dispose();
    }

    private List<EveCharacter> targetCharacters() {
        List<EveCharacter> all = appContext.characterService.listCharacters();
        if (firstCharacterRadio.isSelected() && !all.isEmpty()) {
            return List.of(all.get(0));
        }
        return all;
    }

    private void runNow(Category category) {
        category.nowButton().setEnabled(false);
        runCategories(List.of(category), result -> {
            refreshCooldownStates();
            onCompleted.run();
            reportFailures(result);
        });
    }

    private void runUpdate() {
        List<Category> toRun = categories.stream()
                .filter(c -> c.box().isSelected() && c.box().isEnabled())
                .toList();
        updateButton.setEnabled(false);
        updateButton.setText("Updating...");
        runCategories(toRun, result -> {
            updateButton.setEnabled(true);
            updateButton.setText("Update");
            refreshCooldownStates();
            onCompleted.run();
            reportFailures(result);
            dispose();
        });
    }

    private void runCategories(List<Category> toRun, Consumer<RunResult> onDone) {
        new SwingWorker<RunResult, Void>() {
            @Override
            protected RunResult doInBackground() {
                List<String> refreshed = new ArrayList<>();
                List<String> failures = new ArrayList<>();
                for (Category category : toRun) {
                    List<String> categoryFailures = new ArrayList<>();
                    category.action().accept(categoryFailures);
                    if (categoryFailures.isEmpty()) {
                        appContext.updateCooldownDao.markRefreshed(category.key());
                        refreshed.add(category.key());
                    } else {
                        failures.addAll(categoryFailures.stream().map(f -> category.label() + " - " + f).toList());
                    }
                }
                return new RunResult(refreshed, failures);
            }

            @Override
            protected void done() {
                RunResult result;
                try {
                    result = get();
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Update run failed", e);
                    result = new RunResult(List.of(), List.of("Update - " + describe(e)));
                }
                onDone.accept(result);
            }
        }.execute();
    }

    private void reportFailures(RunResult result) {
        if (result.failures().isEmpty()) {
            return;
        }
        JTextArea text = new JTextArea(String.join("\n", result.failures()));
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        JScrollPane scroll = new JScrollPane(text);
        scroll.setPreferredSize(new Dimension(520, 180));
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.add(new JLabel("<html>These updates failed and weren't counted as done, so you can retry them right away."
                + "<br>Details are also in the log (%USERPROFILE%\\.evefarm\\logs).</html>"), BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        JOptionPane.showMessageDialog(isVisible() ? this : getOwner(), panel, "Some updates failed",
                JOptionPane.WARNING_MESSAGE);
    }

    private void forEachCharacter(List<String> failures, CharacterAction action) {
        for (EveCharacter character : targetCharacters()) {
            attempt(failures, character.characterName(), () -> action.run(character.characterId()));
        }
    }

    private void attempt(List<String> failures, String subject, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Update failed for " + subject, e);
            failures.add(subject + ": " + describe(e));
        }
    }

    static String describe(Throwable error) {
        Throwable deepest = error;
        while (deepest.getCause() != null && deepest.getCause() != deepest) {
            deepest = deepest.getCause();
        }
        String message = deepest.getMessage() != null ? deepest.getMessage() : deepest.getClass().getSimpleName();
        String top = error.getMessage();
        String text = top != null && !top.equals(message) && deepest != error ? top + " (" + message + ")" : message;
        return text.length() > 300 ? text.substring(0, 300) + "..." : text;
    }

    @FunctionalInterface
    private interface CharacterAction {
        void run(long characterId);
    }

    private void runAssets(List<String> failures) {
        forEachCharacter(failures, appContext.assetService::refreshAssetsForCharacter);
    }

    private void runMarketOrders(List<String> failures) {
        forEachCharacter(failures, appContext.marketOrderService::refreshOrdersForCharacter);
    }

    private void runJournal(List<String> failures) {
        forEachCharacter(failures, appContext.journalService::refreshJournalForCharacter);
    }

    private void runTransactions(List<String> failures) {
        forEachCharacter(failures, appContext.transactionService::refreshTransactionsForCharacter);
    }

    private void runContracts(List<String> failures) {
        forEachCharacter(failures, appContext.contractService::refreshContractsForCharacter);
    }

    private void runIndustryJobs(List<String> failures) {
        forEachCharacter(failures, appContext.industryJobService::refreshJobsForCharacter);
    }

    private void runTracker(List<String> failures) {
        forEachCharacter(failures, appContext.trackerSnapshotService::captureSnapshot);
    }

    private void runMarketPrices(List<String> failures) {
        attempt(failures, "Market Prices", appContext.priceService::refreshPrices);
    }

    private void runLoyaltyPoints(List<String> failures) {
        forEachCharacter(failures, appContext.loyaltyPointService::refreshLoyaltyPointsForCharacter);
    }

    private void runKills(List<String> failures) {
        attempt(failures, "Gamelogs", appContext.killService::refreshKillsFromLogs);
    }
}
