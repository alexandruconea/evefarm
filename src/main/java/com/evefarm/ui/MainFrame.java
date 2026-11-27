package com.evefarm.ui;

import com.evefarm.AppContext;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.update.ReleaseInfo;
import com.evefarm.update.UpdateInstaller;
import com.evefarm.util.AppInfo;
import com.evefarm.util.AppPaths;
import com.evefarm.util.Browser;
import com.evefarm.util.Platform;
import com.evefarm.util.Text;
import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatLaf;

import javax.imageio.ImageIO;
import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.Taskbar;
import java.awt.TrayIcon;
import java.awt.Window;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class MainFrame extends JFrame {

    private static final Logger LOG = Logger.getLogger(MainFrame.class.getName());

    private record TabGroup(String key, String label, Icon icon, TabPanel panel, List<SubTabs.Page> pages) {

        boolean hasSubTabs() {
            return pages.size() > 1;
        }

        List<String> pageKeys() {
            return pages.stream().map(SubTabs.Page::key).toList();
        }
    }

    private enum UpdateCheck { MENU, STARTUP, DAILY }

    private static final int FIRST_UPDATE_CHECK_DELAY_MILLIS = 3_000;
    private static final int UPDATE_CHECK_INTERVAL_MILLIS = 24 * 60 * 60 * 1000;
    private static final int NOTIFICATION_TRAY_MILLIS = 20_000;
    private static final String STANDINGS_GROUP = "group.standings";
    private static final String STANDINGS_PAGE = "standings";

    private final AppContext appContext;
    private final Map<String, TabGroup> tabGroups = new LinkedHashMap<>();
    private final JButton updateAvailableButton = new JButton();
    private ReleaseInfo availableRelease;
    private TrayIcon trayIcon;
    private MarketOrdersPanel marketOrdersPanel;
    private StandingsPanel standingsPanel;
    private final MainTabs mainTabs = new MainTabs(TabPanel::onShown, this::showTabMenu);
    private DataUpdater dataUpdater;
    private BackupRestoreFlow backupRestoreFlow;

    public MainFrame(AppContext appContext) {
        initComponents();
        this.appContext = appContext;
        postInit();
    }

    private void postInit() {
        setTitle(AppInfo.NAME + " " + AppInfo.version());
        Image appIcon = loadAppIcon();
        setupTrayIcon(appIcon);
        fileMenu.setIcon(FontAwesome.bar("folder"));
        updateMenu.setIcon(FontAwesome.bar("arrows-rotate"));
        optionsMenu.setIcon(FontAwesome.bar("gear"));
        charactersItem.setIcon(FontAwesome.menu("user"));
        updateItem.setIcon(FontAwesome.menu("arrows-rotate"));
        settingsItem.setIcon(FontAwesome.menu("gear"));
        showTabsItem.setIcon(FontAwesome.menu("eye"));
        backupItem.setIcon(FontAwesome.menu("box-archive"));
        restoreItem.setIcon(FontAwesome.menu("folder-open"));
        exitItem.setIcon(FontAwesome.menu("door-open"));
        backupRestoreFlow = new BackupRestoreFlow(this, appContext, enabled -> {
            backupItem.setEnabled(enabled);
            restoreItem.setEnabled(enabled);
        }, this::exitApplication);
        loadCharacterPortraits();

        marketOrdersPanel = new MarketOrdersPanel(appContext);
        group("group.values", "Values", "chart-column",
                page("values", "Values", "chart-pie", new ValuesPanel(appContext)),
                page("tracker", "Tracker", "chart-line", new TrackerPanel(appContext)),
                page("forecast", "Forecast", "arrow-trend-up", new ForecastPage(appContext)),
                page("assets", "Assets", "box", new AssetsPanel(appContext)));
        group("group.wallet", "Wallet", "wallet",
                page("journal", "Journal", "book", new DataTablePanel<>(appContext, "journal",
                        new JournalTableModel(), "entries", appContext.journalService::getJournalRows)),
                page("transactions", "Transactions", "right-left", new DataTablePanel<>(appContext,
                        "transactions", new TransactionsTableModel(), "transactions",
                        appContext.transactionService::getTransactionRows)),
                page("marketOrders", "Market Orders", "tag", marketOrdersPanel),
                page("contracts", "Contracts", "handshake", new ContractsPanel(appContext)));
        standingsPanel = new StandingsPanel(appContext);
        tab("lpStore", "LP Store", "star", new LpStoreTabs(appContext, this::showStandings));
        tab("kills", "NPC Kills", "crosshairs", new NpcKillsPanel(appContext));
        tab("abyss", "Abyss", "hurricane", new AbyssPanel(appContext, this::showTrayNotification));
        group("group.industry", "Industry", "industry",
                page("industry", "Calculator", "calculator", new IndustryPanel(appContext)),
                page("industryJobs", "Jobs", "hammer", new DataTablePanel<>(appContext, "industryJobs",
                        new IndustryJobsTableModel(), "jobs", () -> appContext.industryJobService.getJobRows(null))),
                page("mining", "Mining", "gem", new MiningPanel(appContext)));
        group(STANDINGS_GROUP, "Standings", "scale-balanced",
                page(STANDINGS_PAGE, "Standings", "ranking-star", standingsPanel),
                page("agents", "Agents", "id-badge", new AgentsPanel(appContext)));
        tab("skills", "Skills", "brain", new SkillsPanel(appContext));

        rebuildTabs();
        dataUpdater = new DataUpdater(appContext);
        dataUpdater.addFinishedListener(result -> {
            onDataUpdated();
            showUpdateFailures(result);
        });
        restoreWindowState();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                saveWindowState();
                appContext.voiceService.close();
                if (trayIcon != null) {
                    SystemTray.getSystemTray().remove(trayIcon);
                }
            }

            @Override
            public void windowIconified(WindowEvent e) {
                if (trayIcon == null) {
                    return;
                }
                try {
                    SystemTray.getSystemTray().add(trayIcon);
                    setVisible(false);
                } catch (AWTException ex) {
                    LOG.log(Level.WARNING, "Failed to add tray icon - minimizing normally instead", ex);
                }
            }
        });

        if (appContext.characterService.listCharacters().isEmpty()) {
            SwingUtilities.invokeLater(this::showCharacters);
        }

        setupUpdates();
        UiSounds.apply("true".equals(appContext.settingsDao.getOrDefault(SettingsDao.UI_SOUNDS, "false")),
                appContext.settingsDao.getInt(SettingsDao.UI_SOUND_VOLUME, UiSounds.DEFAULT_VOLUME));
        setupBeltKillAlerts();
        appContext.notificationService.addListener(notification -> SwingUtilities.invokeLater(
                () -> showTrayNotification(notification.caption(), notification.text())));
        appContext.marketWatchService.addListener(() -> SwingUtilities.invokeLater(() -> {
            if (marketOrdersPanel.isShowing()) {
                marketOrdersPanel.onShown();
            }
        }));
        appContext.schedulerService.addSnapshotListener(() -> SwingUtilities.invokeLater(() -> {
            dataUpdater.reloadCooldowns();
            onDataUpdated();
        }));
    }

    private void setupBeltKillAlerts() {
        Runnable check = () -> {
            try {
                appContext.beltKillMilestoneService.checkForNewMilestone().ifPresent(milestone ->
                        SwingUtilities.invokeLater(() -> showTrayNotification("Belt hunting milestone",
                                String.format(Locale.US, "Your characters have killed %,d NPCs in asteroid belts.",
                                        milestone))));
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to check the belt kill milestone", e);
            }
        };
        appContext.killService.addLogChangeListener(check);
        Thread baseline = new Thread(check, "belt-kill-milestone");
        baseline.setDaemon(true);
        baseline.start();
    }

    private void showTrayNotification(String caption, String text) {
        if (Platform.isLinux() && LinuxNotifications.send(caption, text)) {
            return;
        }
        if (trayIcon == null) {
            LOG.info(caption + ": " + text);
            return;
        }
        SystemTray tray = SystemTray.getSystemTray();
        boolean addedHere = !List.of(tray.getTrayIcons()).contains(trayIcon);
        try {
            if (addedHere) {
                tray.add(trayIcon);
            }
            trayIcon.displayMessage(caption, text, TrayIcon.MessageType.INFO);
        } catch (AWTException e) {
            LOG.log(Level.WARNING, "Could not show the notification: " + text, e);
            return;
        }
        if (addedHere) {
            Timer removal = new Timer(NOTIFICATION_TRAY_MILLIS, e -> {
                if (isVisible()) {
                    tray.remove(trayIcon);
                }
            });
            removal.setRepeats(false);
            removal.start();
        }
    }

    private void setupUpdates() {
        JMenu helpMenu = new JMenu("Help");
        helpMenu.setIcon(FontAwesome.bar("circle-question"));
        JMenuItem checkItem = new JMenuItem("Check for Updates...", FontAwesome.menu("arrows-rotate"));
        checkItem.addActionListener(e -> checkForUpdates(UpdateCheck.MENU));
        JMenuItem aboutItem = new JMenuItem("About " + AppInfo.NAME, FontAwesome.menu("file-lines"));
        aboutItem.addActionListener(e -> showAbout());
        helpMenu.add(checkItem);
        helpMenu.add(aboutItem);
        jMenuBar1.remove(updateMenu);
        jMenuBar1.remove(optionsMenu);
        jMenuBar1.add(Box.createHorizontalStrut(12));
        jMenuBar1.add(mainTabs.strip());
        jMenuBar1.add(Box.createHorizontalStrut(12));
        jMenuBar1.add(updateMenu);
        jMenuBar1.add(optionsMenu);
        jMenuBar1.add(helpMenu);

        updateAvailableButton.putClientProperty("JButton.buttonType", "toolBarButton");
        updateAvailableButton.setIcon(Icons.REFRESH);
        updateAvailableButton.setFocusable(false);
        updateAvailableButton.setVisible(false);
        Color accent = UIManager.getColor("Component.accentColor");
        if (accent != null) {
            updateAvailableButton.setForeground(accent);
        }
        updateAvailableButton.addActionListener(e -> {
            if (availableRelease != null) {
                showUpdateDialog(availableRelease);
            }
        });
        jMenuBar1.add(Box.createHorizontalGlue());
        jMenuBar1.add(updateAvailableButton);
        jMenuBar1.add(new UpdateButton(dataUpdater));
        getRootPane().putClientProperty(FlatClientProperties.TITLE_BAR_SHOW_TITLE, false);
        getRootPane().putClientProperty(FlatClientProperties.TITLE_BAR_SHOW_ICON, false);
        if (!(UIManager.getLookAndFeel() instanceof FlatLaf)) {
            WindowChrome.install(this, jMenuBar1);
        }

        announceIfJustUpdated();
        Thread cleanup = new Thread(UpdateInstaller::cleanUpAfterUpdate, "update-cleanup");
        cleanup.setDaemon(true);
        cleanup.start();

        if (!AppInfo.isDevBuild()) {
            Timer startup = new Timer(FIRST_UPDATE_CHECK_DELAY_MILLIS, e -> checkForUpdates(UpdateCheck.STARTUP));
            startup.setRepeats(false);
            startup.start();
            Timer daily = new Timer(UPDATE_CHECK_INTERVAL_MILLIS, e -> checkForUpdates(UpdateCheck.DAILY));
            daily.start();
        }
    }

    private void announceIfJustUpdated() {
        String current = AppInfo.version();
        String previous = appContext.settingsDao.get(SettingsDao.LAST_RUN_VERSION).orElse(null);
        appContext.settingsDao.set(SettingsDao.LAST_RUN_VERSION, current);
        if (previous != null && !AppInfo.isDevBuild() && AppInfo.compareVersions(current, previous) > 0) {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                    AppInfo.NAME + " was updated from " + previous + " to " + current + ".",
                    "Updated", JOptionPane.INFORMATION_MESSAGE));
        }
        UpdateInstaller.takeUpdateFailure().ifPresent(reason -> SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(this,
                        "The last update couldn't be installed, so " + AppInfo.NAME + " is still on version "
                                + current + ".\n\nReason: " + reason
                                + "\n\nYou can try again from Help > Check for Updates. Details are in\n"
                                + AppPaths.appDataDir().resolve("logs").resolve("updater.log"),
                        "Update Failed", JOptionPane.WARNING_MESSAGE)));
    }

    private void checkForUpdates(UpdateCheck check) {
        boolean userAsked = check == UpdateCheck.MENU;
        if (!userAsked && !appContext.updateService.isAutoCheckEnabled()) {
            return;
        }
        new SwingWorker<Optional<ReleaseInfo>, Void>() {
            @Override
            protected Optional<ReleaseInfo> doInBackground() throws Exception {
                return appContext.updateService.findNewerRelease(AppInfo.version());
            }

            @Override
            protected void done() {
                Optional<ReleaseInfo> release;
                try {
                    release = get();
                } catch (Exception e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    LOG.log(Level.INFO, "Update check failed", cause);
                    if (userAsked) {
                        JOptionPane.showMessageDialog(MainFrame.this,
                                "Couldn't check for updates:\n" + cause.getMessage(),
                                "Check for Updates", JOptionPane.WARNING_MESSAGE);
                    }
                    return;
                }
                if (release.isEmpty()) {
                    if (userAsked) {
                        JOptionPane.showMessageDialog(MainFrame.this,
                                "You have the latest version (" + AppInfo.version() + ").",
                                "Check for Updates", JOptionPane.INFORMATION_MESSAGE);
                    }
                    return;
                }
                if (!userAsked && appContext.updateService.isSkipped(release.get())) {
                    return;
                }
                availableRelease = release.get();
                updateAvailableButton.setText("Update available: " + availableRelease.version());
                updateAvailableButton.setVisible(true);
                if (userAsked || (check == UpdateCheck.STARTUP && isVisible())) {
                    showUpdateDialog(availableRelease);
                } else if (!isVisible() || (getExtendedState() & JFrame.ICONIFIED) != 0) {
                    showTrayNotification(AppInfo.NAME + " " + availableRelease.version() + " is available",
                            "Open EVE Farm to update.");
                }
            }
        }.execute();
    }

    private void showUpdateDialog(ReleaseInfo release) {
        new AppUpdateDialog(this, appContext, release, this::exitApplication).setVisible(true);
        if (appContext.updateService.isSkipped(release)) {
            updateAvailableButton.setVisible(false);
        }
    }

    private void showAbout() {
        Object[] options = {"GitHub Page", "Close"};
        int choice = JOptionPane.showOptionDialog(this,
                AppInfo.NAME + " " + AppInfo.version() + "\n\n"
                        + "Your data: " + AppPaths.appDataDir() + "\n"
                        + "Backups: " + AppPaths.backupDir() + "\n"
                        + "Logs: " + AppPaths.appDataDir().resolve("logs"),
                "About " + AppInfo.NAME, JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null,
                options, options[1]);
        if (choice == 0) {
            try {
                Browser.open(AppInfo.HOME_PAGE);
            } catch (Exception e) {
                LOG.log(Level.INFO, "Couldn't open the GitHub page", e);
            }
        }
    }

    private Image loadAppIcon() {
        try (InputStream in = getClass().getResourceAsStream("/icons/app-icon.png")) {
            if (in == null) {
                return null;
            }
            Image icon = ImageIO.read(in);
            if (icon == null) {
                return null;
            }
            setIconImage(icon);
            if (Taskbar.isTaskbarSupported()) {
                Taskbar taskbar = Taskbar.getTaskbar();
                if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                    taskbar.setIconImage(icon);
                }
            }
            return icon;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Couldn't load the app icon", e);
            return null;
        }
    }

    private void setupTrayIcon(Image appIcon) {
        if (Platform.isLinux() || !SystemTray.isSupported() || appIcon == null) {
            return;
        }
        PopupMenu popup = new PopupMenu();
        MenuItem openItem = new MenuItem("Open");
        openItem.addActionListener(e -> restoreFromTray());
        MenuItem exit = new MenuItem("Exit");
        exit.addActionListener(e -> exitApplication());
        popup.add(openItem);
        popup.addSeparator();
        popup.add(exit);

        trayIcon = new TrayIcon(appIcon, AppInfo.NAME + " " + AppInfo.version(), popup);
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener(e -> restoreFromTray());
    }

    public void bringToFront() {
        restoreFromTray();
    }

    private void restoreFromTray() {
        if (trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
        }
        setExtendedState(getExtendedState() & ~JFrame.ICONIFIED);
        setVisible(true);
        toFront();
        requestFocus();
    }

    private void restoreWindowState() {
        Integer width = parseIntSetting(SettingsDao.WINDOW_WIDTH);
        Integer height = parseIntSetting(SettingsDao.WINDOW_HEIGHT);
        boolean maximized = "true".equals(appContext.settingsDao.getOrDefault(SettingsDao.WINDOW_MAXIMIZED, "false"));

        if (width != null && height != null) {
            setSize(new Dimension(width, height));
        }
        setLocationRelativeTo(null);
        if (maximized) {
            setExtendedState(JFrame.MAXIMIZED_BOTH);
        }
    }

    private Integer parseIntSetting(String key) {
        try {
            return appContext.settingsDao.get(key).map(Integer::parseInt).orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void saveWindowState() {
        boolean maximized = (getExtendedState() & JFrame.MAXIMIZED_BOTH) == JFrame.MAXIMIZED_BOTH;
        appContext.settingsDao.set(SettingsDao.WINDOW_MAXIMIZED, String.valueOf(maximized));
        if (!maximized) {
            appContext.settingsDao.set(SettingsDao.WINDOW_WIDTH, String.valueOf(getWidth()));
            appContext.settingsDao.set(SettingsDao.WINDOW_HEIGHT, String.valueOf(getHeight()));
        }
    }

    private void rebuildTabs() {
        Set<String> hidden = TabVisibility.readHidden(appContext.settingsDao);
        List<MainTabs.Tab> shown = new ArrayList<>();
        for (TabGroup group : orderedGroups()) {
            boolean visible = group.hasSubTabs() && group.panel() instanceof SubTabs subTabs
                    ? subTabs.showPages(hidden) : !hidden.contains(group.key());
            if (visible) {
                shown.add(new MainTabs.Tab(group.label(), group.icon(), group.panel()));
            }
        }
        mainTabs.setTabs(shown);
    }

    private void showTabMenu(TabPanel panel, MouseEvent e) {
        tabGroups.values().stream().filter(group -> group.panel() == panel).findFirst()
                .ifPresent(group -> showTabMenu(e, group.pageKeys()));
    }

    private void showTabMenu(MouseEvent e, List<String> pageKeys) {
        Set<String> hidden = TabVisibility.readHidden(appContext.settingsDao);
        boolean othersShown = tabGroups.values().stream().flatMap(group -> group.pageKeys().stream())
                .anyMatch(key -> !hidden.contains(key) && !pageKeys.contains(key));
        JPopupMenu menu = new JPopupMenu();
        JMenuItem close = new JMenuItem("Close Tab", FontAwesome.menu("xmark"));
        close.setEnabled(othersShown);
        close.addActionListener(event -> closeTab(pageKeys));
        JMenuItem showTabs = new JMenuItem("Show Tabs...", FontAwesome.menu("eye"));
        showTabs.addActionListener(event -> showTabVisibility());
        menu.add(close);
        menu.addSeparator();
        menu.add(showTabs);
        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    private void showStandings(StandingTarget target) {
        Set<String> hidden = TabVisibility.readHidden(appContext.settingsDao);
        if (hidden.remove(STANDINGS_PAGE)) {
            TabVisibility.writeHidden(appContext.settingsDao, hidden);
            rebuildTabs();
        }
        if (!(tabGroups.get(STANDINGS_GROUP).panel() instanceof SubTabs subTabs)) {
            return;
        }
        mainTabs.select(subTabs);
        subTabs.pages().stream()
                .filter(page -> page.key().equals(STANDINGS_PAGE))
                .findFirst()
                .ifPresent(subTabs::select);
        standingsPanel.reveal(target);
    }

    private void closeTab(List<String> pageKeys) {
        Set<String> hidden = TabVisibility.readHidden(appContext.settingsDao);
        hidden.addAll(pageKeys);
        TabVisibility.writeHidden(appContext.settingsDao, hidden);
        rebuildTabs();
    }

    private void group(String key, String label, String icon, SubTabs.Page... pages) {
        tabGroups.put(key, new TabGroup(key, label, FontAwesome.bar(icon),
                new SubTabs(appContext.settingsDao, key, pages), List.of(pages)));
    }

    private void tab(String key, String label, String icon, TabPanel panel) {
        Icon barIcon = FontAwesome.bar(icon);
        tabGroups.put(key, new TabGroup(key, label, barIcon, panel,
                List.of(new SubTabs.Page(key, label, barIcon, panel))));
    }

    private static SubTabs.Page page(String key, String title, String icon, TabPanel panel) {
        return new SubTabs.Page(key, title, FontAwesome.menu(icon), panel);
    }

    private void onCharactersChanged() {
        loadCharacterPortraits();
        tabGroups.values().forEach(group -> group.panel().refreshCharacterFilter());
    }

    private void loadCharacterPortraits() {
        appContext.characterPortraits.reload(() -> {
            for (Window window : Window.getWindows()) {
                window.repaint();
            }
        });
    }

    private List<TabGroup> orderedGroups() {
        Map<String, List<String>> pageKeys = new LinkedHashMap<>();
        tabGroups.forEach((key, group) -> pageKeys.put(key, group.pageKeys()));
        return TabVisibility.orderGroups(TabVisibility.readOrder(appContext.settingsDao), pageKeys).stream()
                .map(tabGroups::get).toList();
    }

    private JMenuItem charactersItem;
    private JMenuItem exitItem;
    private JMenu fileMenu;
    private JMenuBar jMenuBar1;
    private JSeparator jSeparator1;
    private JMenu optionsMenu;
    private JMenuItem settingsItem;
    private JMenuItem showTabsItem;
    private JSeparator jSeparator2;
    private JMenuItem backupItem;
    private JMenuItem restoreItem;
    private JMenu updateMenu;
    private JMenuItem updateItem;

    private void initComponents() {

        jMenuBar1 = new JMenuBar();
        fileMenu = new JMenu();
        charactersItem = new JMenuItem();
        jSeparator1 = new JSeparator();
        exitItem = new JMenuItem();
        updateMenu = new JMenu();
        updateItem = new JMenuItem();
        optionsMenu = new JMenu();
        settingsItem = new JMenuItem();
        showTabsItem = new JMenuItem();
        jSeparator2 = new JSeparator();
        backupItem = new JMenuItem();
        restoreItem = new JMenuItem();

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setTitle("EVE Farm");
        setMinimumSize(new Dimension(1000, 650));

        fileMenu.setText("File");

        charactersItem.setText("Characters...");
        charactersItem.addActionListener(e -> showCharacters());
        fileMenu.add(charactersItem);
        fileMenu.add(jSeparator1);

        exitItem.setText("Exit");
        exitItem.addActionListener(e -> exitApplication());
        fileMenu.add(exitItem);

        jMenuBar1.add(fileMenu);

        updateMenu.setText("Update");

        updateItem.setText("Update...");
        updateItem.addActionListener(e -> showDataUpdate());
        updateMenu.add(updateItem);

        jMenuBar1.add(updateMenu);

        optionsMenu.setText("Options");

        settingsItem.setText("Settings...");
        settingsItem.addActionListener(e -> showSettings());
        optionsMenu.add(settingsItem);

        showTabsItem.setText("Show Tabs...");
        showTabsItem.addActionListener(e -> showTabVisibility());
        optionsMenu.add(showTabsItem);
        optionsMenu.add(jSeparator2);

        backupItem.setText("Backup Data...");
        backupItem.addActionListener(e -> backupRestoreFlow.backup());
        optionsMenu.add(backupItem);

        restoreItem.setText("Restore Data...");
        restoreItem.addActionListener(e -> backupRestoreFlow.restore());
        optionsMenu.add(restoreItem);

        jMenuBar1.add(optionsMenu);

        setJMenuBar(jMenuBar1);

        mainTabs.content().setPreferredSize(new Dimension(1000, 650));
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(mainTabs.content(), BorderLayout.CENTER);
        getContentPane().add(LegalFooter.create(), BorderLayout.SOUTH);

        pack();
    }

    private void showCharacters() {
        CharactersDialog dialog = new CharactersDialog(this, appContext, this::onCharactersChanged);
        dialog.setVisible(true);
    }

    private void showSettings() {
        SettingsDialog dialog = new SettingsDialog(this, appContext.settingsDao);
        dialog.setVisible(true);
    }

    private void showTabVisibility() {
        List<TabVisibilityDialog.Group> groups = orderedGroups().stream()
                .map(group -> new TabVisibilityDialog.Group(group.key(), group.label(), group.pages().stream()
                        .map(page -> new TabVisibilityDialog.Page(page.key(), page.title())).toList()))
                .toList();
        TabVisibilityDialog dialog = new TabVisibilityDialog(this, appContext.settingsDao, groups, this::rebuildTabs);
        dialog.setVisible(true);
    }

    private void exitApplication() {
        dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
    }

    private void showDataUpdate() {
        new UpdateDialog(this, dataUpdater).setVisible(true);
    }

    private void showUpdateFailures(DataUpdater.Result result) {
        List<String> failures = result.failures();
        List<String> needLogin = result.needLogin();
        if (failures.isEmpty() && needLogin.isEmpty()) {
            return;
        }
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        if (!needLogin.isEmpty()) {
            panel.add(new JLabel("<html><b>Add " + Text.escapeHtml(String.join(", ", needLogin)) + " again.</b><br>"
                    + "EVE Farm can't use their saved logins here: EVE no longer accepts them, or they come from a "
                    + "backup<br>restored without its backup password. Adding a character again keeps all its "
                    + "history.</html>"), BorderLayout.NORTH);
        }
        if (!failures.isEmpty()) {
            JTextArea text = new JTextArea(String.join("\n", failures));
            TextAreaStyler.scrollable(text);
            JScrollPane scroll = new JScrollPane(text);
            scroll.setPreferredSize(new Dimension(520, 180));
            JPanel failed = new JPanel(new BorderLayout(0, 8));
            failed.add(new JLabel("<html>These updates failed and weren't counted as done, so you can retry them "
                    + "right away.<br>Details are also in the log (" + AppPaths.appDataDir().resolve("logs")
                    + ").</html>"), BorderLayout.NORTH);
            failed.add(scroll, BorderLayout.CENTER);
            panel.add(failed, BorderLayout.CENTER);
        }
        String title = failures.isEmpty() ? "Add your characters again" : "Some updates failed";
        if (needLogin.isEmpty()) {
            JOptionPane.showMessageDialog(this, panel, title, JOptionPane.WARNING_MESSAGE);
            return;
        }
        Object[] options = {"Add Characters...", "Close"};
        int choice = JOptionPane.showOptionDialog(this, panel, title, JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE, null, options, options[0]);
        if (choice == 0) {
            showCharacters();
        }
    }

    private void onDataUpdated() {
        TabPanel selected = mainTabs.selected();
        tabGroups.values().forEach(group -> group.panel().onDataUpdated(group.panel() == selected));
    }
}
