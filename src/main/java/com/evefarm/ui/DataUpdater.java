package com.evefarm.ui;

import com.evefarm.AppContext;
import com.evefarm.esi.EsiException;
import com.evefarm.model.EveCharacter;
import com.evefarm.service.UpdateCategories;

import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

final class DataUpdater {

    private static final Logger LOG = Logger.getLogger(DataUpdater.class.getName());
    private static final int COOLDOWN_RELOAD_MILLIS = 15_000;

    record Category(String key, String label, Duration cooldown, boolean usesLogins, Step step) {
    }

    record Result(List<String> failures, List<String> needLogin) {
    }

    @FunctionalInterface
    interface Step {
        void run(List<EveCharacter> characters, List<String> failures);
    }

    @FunctionalInterface
    private interface CharacterAction {
        void run(long characterId);
    }

    private final AppContext appContext;
    private final List<Category> categories;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "evefarm-update");
        thread.setDaemon(true);
        return thread;
    });
    private final Set<String> pending = new LinkedHashSet<>();
    private final Map<String, Instant> lastRefreshed = new HashMap<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private final List<Consumer<Result>> finishedListeners = new ArrayList<>();
    private Category current;
    private boolean reloading;

    DataUpdater(AppContext appContext) {
        this.appContext = appContext;
        this.categories = List.of(
                new Category(UpdateCategories.MARKET_PRICES, "Market Prices", Duration.ofHours(1), false,
                        (characters, failures) -> appContext.priceService.refreshPrices()),
                new Category(UpdateCategories.ASSETS, "Assets", Duration.ofHours(1), true,
                        perCharacter(appContext.assetService::refreshAssetsForCharacter)),
                new Category(UpdateCategories.MARKET_ORDERS, "Market Orders", Duration.ofMinutes(20), true,
                        perCharacter(appContext.marketOrderService::refreshOrdersForCharacter)),
                new Category(UpdateCategories.JOURNAL, "Journal", Duration.ofHours(1), true,
                        perCharacter(appContext.journalService::refreshJournalForCharacter)),
                new Category(UpdateCategories.TRANSACTIONS, "Transactions", Duration.ofHours(1), true,
                        perCharacter(appContext.transactionService::refreshTransactionsForCharacter)),
                new Category(UpdateCategories.CONTRACTS, "Contracts", Duration.ofHours(1), true,
                        perCharacter(appContext.contractService::refreshContractsForCharacter)),
                new Category(UpdateCategories.INDUSTRY_JOBS, "Industry Jobs", Duration.ofHours(1), true,
                        perCharacter(appContext.industryJobService::refreshJobsForCharacter)),
                new Category(UpdateCategories.TRACKER, "Tracker Snapshot", Duration.ofHours(1), true,
                        perCharacter(appContext.trackerSnapshotService::captureSnapshot)),
                new Category(UpdateCategories.LOYALTY_POINTS, "Loyalty Points", Duration.ofHours(1), true,
                        perCharacter(appContext.loyaltyPointService::refreshLoyaltyPointsForCharacter)),
                new Category(UpdateCategories.KILLS, "NPC Kills", Duration.ofHours(1), false,
                        (characters, failures) -> attempt(failures, "Gamelogs",
                                appContext.killService::refreshKillsFromLogs)),
                new Category(UpdateCategories.MINING, "Mining Ledger", Duration.ofMinutes(10), true,
                        perCharacter(appContext.miningService::refreshLedgerForCharacter)),
                new Category(UpdateCategories.STANDINGS, "Standings", Duration.ofHours(1), true,
                        perCharacter(appContext.standingService::refreshStandingsForCharacter)),
                new Category(UpdateCategories.SKILLS, "Skills", Duration.ofMinutes(5), true,
                        perCharacter(appContext.skillService::refreshSkillsForCharacter)));
        new Timer(COOLDOWN_RELOAD_MILLIS, e -> reloadCooldowns()).start();
        reloadCooldowns();
    }

    List<Category> categories() {
        return categories;
    }

    void addListener(Runnable listener) {
        listeners.add(listener);
    }

    void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    void addFinishedListener(Consumer<Result> listener) {
        finishedListeners.add(listener);
    }

    boolean busy() {
        return !pending.isEmpty();
    }

    boolean isPending(Category category) {
        return pending.contains(category.key());
    }

    Instant readyAt(Category category) {
        return readyAt(category, lastRefreshed);
    }

    List<String> readyKeys(Instant now) {
        return categories.stream()
                .filter(category -> !isPending(category) && !now.isBefore(readyAt(category)))
                .map(Category::key)
                .toList();
    }

    String status(Instant now) {
        return status(categories, lastRefreshed, pending, current, now);
    }

    void update(Collection<String> keys, boolean firstCharacterOnly) {
        List<Category> toRun = categories.stream()
                .filter(category -> keys.contains(category.key()) && pending.add(category.key()))
                .toList();
        if (toRun.isEmpty()) {
            return;
        }
        fireChanged();
        executor.execute(() -> run(toRun, firstCharacterOnly));
    }

    void reloadCooldowns() {
        if (reloading) {
            return;
        }
        reloading = true;
        new SwingWorker<Map<String, Instant>, Void>() {
            @Override
            protected Map<String, Instant> doInBackground() {
                return appContext.updateCooldownDao.findAll();
            }

            @Override
            protected void done() {
                reloading = false;
                try {
                    get().forEach((key, refreshed) -> lastRefreshed.merge(key, refreshed, DataUpdater::later));
                    fireChanged();
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Couldn't read when the data was last updated", e);
                }
            }
        }.execute();
    }

    private void run(List<Category> toRun, boolean firstCharacterOnly) {
        List<String> failures = new ArrayList<>();
        Set<String> needLogin = new LinkedHashSet<>();
        Set<Long> loginsFromAnotherSystem = loginsFromAnotherSystem();
        for (Category category : toRun) {
            SwingUtilities.invokeLater(() -> {
                current = category;
                fireChanged();
            });
            List<String> categoryFailures = new ArrayList<>();
            boolean skippedCharacters = false;
            try {
                List<EveCharacter> characters = characters(firstCharacterOnly);
                if (category.usesLogins()) {
                    List<EveCharacter> withoutLogin = characters.stream()
                            .filter(character -> loginsFromAnotherSystem.contains(character.characterId()))
                            .toList();
                    withoutLogin.forEach(character -> needLogin.add(character.characterName()));
                    characters = characters.stream().filter(character -> !withoutLogin.contains(character)).toList();
                    skippedCharacters = !withoutLogin.isEmpty();
                }
                category.step().run(characters, categoryFailures);
                if (categoryFailures.isEmpty() && !skippedCharacters) {
                    appContext.updateCooldownDao.markRefreshed(category.key());
                }
            } catch (RuntimeException | Error e) {
                LOG.log(Level.WARNING, "Update failed: " + category.label(), e);
                categoryFailures.add(describe(e));
            }
            Instant finished = Instant.now();
            boolean refreshed = categoryFailures.isEmpty() && !skippedCharacters;
            categoryFailures.forEach(failure -> failures.add(category.label() + " - " + failure));
            SwingUtilities.invokeLater(() -> {
                pending.remove(category.key());
                if (refreshed) {
                    lastRefreshed.merge(category.key(), finished, DataUpdater::later);
                }
                if (current == category) {
                    current = null;
                }
                fireChanged();
            });
        }
        Result result = new Result(List.copyOf(failures), List.copyOf(needLogin));
        SwingUtilities.invokeLater(() -> finishedListeners.forEach(listener -> listener.accept(result)));
    }

    private Set<Long> loginsFromAnotherSystem() {
        try {
            return appContext.tokenDao.characterIdsWithLoginsFromAnotherSystem();
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Couldn't check which saved logins come from another system", e);
            return Set.of();
        }
    }

    private List<EveCharacter> characters(boolean firstCharacterOnly) {
        List<EveCharacter> all = appContext.characterService.listCharacters();
        return firstCharacterOnly && !all.isEmpty() ? List.of(all.getFirst()) : all;
    }

    private void fireChanged() {
        List.copyOf(listeners).forEach(Runnable::run);
    }

    private static Step perCharacter(CharacterAction action) {
        return (characters, failures) -> {
            for (EveCharacter character : characters) {
                attempt(failures, character.characterName(), () -> action.run(character.characterId()));
            }
        };
    }

    private static void attempt(List<String> failures, String subject, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Update failed for " + subject, e);
            failures.add(subject + ": " + describe(e));
        }
    }

    private static Instant later(Instant first, Instant second) {
        return first.isAfter(second) ? first : second;
    }

    static Instant readyAt(Category category, Map<String, Instant> lastRefreshed) {
        Instant last = lastRefreshed.get(category.key());
        return last == null ? Instant.EPOCH : last.plus(category.cooldown());
    }

    static String status(List<Category> categories, Map<String, Instant> lastRefreshed, Set<String> pending,
                         Category current, Instant now) {
        if (!pending.isEmpty()) {
            String running = current == null ? "Updating..." : "Updating " + current.label() + "...";
            List<String> waiting = categories.stream()
                    .filter(category -> pending.contains(category.key()) && category != current)
                    .map(Category::label)
                    .toList();
            return waiting.isEmpty() ? running : running + "\nNext: " + String.join(", ", waiting);
        }
        List<String> ready = new ArrayList<>();
        Map<String, List<String>> waiting = new LinkedHashMap<>();
        categories.stream()
                .sorted(Comparator.comparing(category -> readyAt(category, lastRefreshed)))
                .forEach(category -> {
                    Instant readyAt = readyAt(category, lastRefreshed);
                    if (now.isBefore(readyAt)) {
                        waiting.computeIfAbsent(waitText(Duration.between(now, readyAt)), wait -> new ArrayList<>())
                                .add(category.label());
                    } else {
                        ready.add(category.label());
                    }
                });
        StringBuilder text = new StringBuilder();
        if (ready.isEmpty()) {
            text.append("Everything was updated recently. The next update can run in ")
                    .append(waiting.keySet().iterator().next()).append('.');
        } else if (waiting.isEmpty()) {
            text.append("Click to update all your data.");
        } else {
            text.append("Click to update ").append(String.join(", ", ready)).append('.');
        }
        if (!waiting.isEmpty()) {
            text.append("\n\nCan be updated again in:");
            waiting.forEach((wait, labels) -> text.append('\n').append(wait).append(": ")
                    .append(String.join(", ", labels)));
        }
        return text.toString();
    }

    static String waitText(Duration duration) {
        long seconds = Math.max(1, duration.toSeconds());
        if (seconds < 60) {
            return seconds + " s";
        }
        long minutes = (seconds + 59) / 60;
        if (minutes < 60) {
            return minutes + " min";
        }
        return String.format(Locale.US, "%d h %02d min", minutes / 60, minutes % 60);
    }

    static String describe(Throwable error) {
        Throwable deepest = error;
        while (deepest.getCause() != null && deepest.getCause() != deepest) {
            deepest = deepest.getCause();
        }
        if (deepest instanceof EsiException esi && esi.statusCode() >= 502 && esi.statusCode() <= 504) {
            return "EVE's servers didn't answer (HTTP " + esi.statusCode() + "). Around 11:00 EVE time that's the "
                    + "daily downtime - try again in a few minutes.";
        }
        String problem = connectionProblem(error);
        String message = problem != null ? problem
                : deepest.getMessage() != null ? deepest.getMessage() : deepest.getClass().getSimpleName();
        String top = error.getMessage();
        String text = top != null && !top.equals(message) && deepest != error ? top + " (" + message + ")" : message;
        return text.length() > 300 ? text.substring(0, 300) + "..." : text;
    }

    private static String connectionProblem(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            if (cause instanceof HttpTimeoutException) {
                return "the server didn't answer in time - try again later";
            }
            if (cause instanceof ConnectException) {
                return "couldn't connect to the server - it may be down, or the internet connection is off; "
                        + "try again later";
            }
            if (cause instanceof UnknownHostException) {
                return "the server's address couldn't be found - check the internet connection";
            }
        }
        return null;
    }
}
