package com.evefarm.ui;

import com.evefarm.AppContext;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.service.BackupRestoreService;
import com.evefarm.update.ReleaseInfo;
import com.evefarm.update.UpdateInstaller;
import com.evefarm.util.AppInfo;
import com.evefarm.util.AppPaths;
import com.formdev.flatlaf.FlatLaf;

import javax.imageio.ImageIO;
import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.Taskbar;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
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

    private record TabSpec(String label, Icon icon, Component component) {
    }

    private enum UpdateCheck { MENU, STARTUP, DAILY }

    private static final int FIRST_UPDATE_CHECK_DELAY_MILLIS = 3_000;
    private static final int UPDATE_CHECK_INTERVAL_MILLIS = 24 * 60 * 60 * 1000;
    private static final int NOTIFICATION_TRAY_MILLIS = 20_000;

    private final AppContext appContext;
    private final Map<String, TabSpec> tabSpecs = new LinkedHashMap<>();
    private final JButton updateAvailableButton = new JButton();
    private ReleaseInfo availableRelease;
    private TrayIcon trayIcon;
    private AssetsPanel assetsPanel;
    private TrackerPanel trackerPanel;
    private JournalPanel journalPanel;
    private MarketOrdersPanel marketOrdersPanel;
    private TransactionsPanel transactionsPanel;
    private ContractsPanel contractsPanel;
    private IndustryJobsPanel industryJobsPanel;
    private ValuesPanel valuesPanel;
    private LpStorePanel lpStorePanel;
    private NpcKillsPanel killsPanel;
    private AgentsPanel agentsPanel;
    private AbyssPanel abyssPanel;
    private MiningPanel miningPanel;
    private StandingsPanel standingsPanel;
    private SkillsPanel skillsPanel;
    private IndustryPanel industryPanel;

    public MainFrame(AppContext appContext) {
        initComponents();
        this.appContext = appContext;
        postInit();
    }

    private void postInit() {
        setTitle(AppInfo.NAME + " " + AppInfo.version());
        Image appIcon = loadAppIcon();
        setupTrayIcon(appIcon);
        charactersItem.setIcon(Icons.PERSON);
        updateItem.setIcon(Icons.REFRESH);
        settingsItem.setIcon(Icons.GEAR);
        showTabsItem.setIcon(Icons.EYE);
        backupItem.setIcon(Icons.ARCHIVE);
        restoreItem.setIcon(Icons.LOAD);
        exitItem.setIcon(Icons.DOOR);

        assetsPanel = new AssetsPanel(appContext);
        trackerPanel = new TrackerPanel(appContext);
        journalPanel = new JournalPanel(appContext);
        marketOrdersPanel = new MarketOrdersPanel(appContext);
        transactionsPanel = new TransactionsPanel(appContext);
        contractsPanel = new ContractsPanel(appContext);
        industryJobsPanel = new IndustryJobsPanel(appContext);
        valuesPanel = new ValuesPanel(appContext);
        lpStorePanel = new LpStorePanel(appContext);
        killsPanel = new NpcKillsPanel(appContext);
        agentsPanel = new AgentsPanel(appContext);
        abyssPanel = new AbyssPanel(appContext, this::showTrayNotification);
        miningPanel = new MiningPanel(appContext);
        standingsPanel = new StandingsPanel(appContext);
        skillsPanel = new SkillsPanel(appContext);
        industryPanel = new IndustryPanel(appContext);

        tabSpecs.put("assets", new TabSpec("Assets", Icons.COIN, assetsPanel));
        tabSpecs.put("tracker", new TabSpec("Tracker", Icons.CHART, trackerPanel));
        tabSpecs.put("journal", new TabSpec("Journal", Icons.NOTEBOOK, journalPanel));
        tabSpecs.put("marketOrders", new TabSpec("Market Orders", Icons.CLIPBOARD, marketOrdersPanel));
        tabSpecs.put("transactions", new TabSpec("Transactions", Icons.SWAP, transactionsPanel));
        tabSpecs.put("contracts", new TabSpec("Contracts", Icons.DOCUMENT, contractsPanel));
        tabSpecs.put("industryJobs", new TabSpec("Industry Jobs", Icons.FACTORY, industryJobsPanel));
        tabSpecs.put("values", new TabSpec("Values", Icons.VALUE, valuesPanel));
        tabSpecs.put("lpStore", new TabSpec("LP Store", Icons.LP, lpStorePanel));
        tabSpecs.put("kills", new TabSpec("NPC Kills", Icons.CROSSHAIR, killsPanel));
        tabSpecs.put("abyss", new TabSpec("Abyss", Icons.ABYSS, abyssPanel));
        tabSpecs.put("mining", new TabSpec("Mining", Icons.MINING, miningPanel));
        tabSpecs.put("agents", new TabSpec("Agents", Icons.MEDAL, agentsPanel));
        tabSpecs.put("standings", new TabSpec("Standings", Icons.STANDINGS, standingsPanel));
        tabSpecs.put("skills", new TabSpec("Skills", Icons.SKILLS, skillsPanel));
        tabSpecs.put("industry", new TabSpec("Industry Calculator", Icons.CALCULATOR, industryPanel));

        rebuildTabs();
        installTabMenu();

        tabbedPane.addChangeListener(e -> {
            Component selected = tabbedPane.getSelectedComponent();
            if (selected == assetsPanel) {
                assetsPanel.onShown();
            } else if (selected == trackerPanel) {
                trackerPanel.onShown();
            } else if (selected == journalPanel) {
                journalPanel.onShown();
            } else if (selected == marketOrdersPanel) {
                marketOrdersPanel.onShown();
            } else if (selected == transactionsPanel) {
                transactionsPanel.onShown();
            } else if (selected == contractsPanel) {
                contractsPanel.onShown();
            } else if (selected == industryJobsPanel) {
                industryJobsPanel.onShown();
            } else if (selected == valuesPanel) {
                valuesPanel.onShown();
            } else if (selected == lpStorePanel) {
                lpStorePanel.onShown();
            } else if (selected == killsPanel) {
                killsPanel.onShown();
            } else if (selected == agentsPanel) {
                agentsPanel.onShown();
            } else if (selected == abyssPanel) {
                abyssPanel.onShown();
            } else if (selected == miningPanel) {
                miningPanel.onShown();
            } else if (selected == standingsPanel) {
                standingsPanel.onShown();
            } else if (selected == skillsPanel) {
                skillsPanel.onShown();
            } else if (selected == industryPanel) {
                industryPanel.onShown();
            }
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
            SwingUtilities.invokeLater(() -> charactersItemActionPerformed(null));
        }

        setupUpdates();
        setupBeltKillAlerts();
        appContext.notificationService.addListener(notification -> SwingUtilities.invokeLater(
                () -> showTrayNotification(notification.caption(), notification.text())));
        appContext.marketWatchService.addListener(() -> SwingUtilities.invokeLater(() -> {
            if (tabbedPane.getSelectedComponent() == marketOrdersPanel) {
                marketOrdersPanel.onShown();
            }
        }));
        appContext.schedulerService.addSnapshotListener(() -> SwingUtilities.invokeLater(this::onDataUpdated));
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
        JMenuItem checkItem = new JMenuItem("Check for Updates...", Icons.REFRESH);
        checkItem.addActionListener(e -> checkForUpdates(UpdateCheck.MENU));
        JMenuItem aboutItem = new JMenuItem("About " + AppInfo.NAME, Icons.DOCUMENT);
        aboutItem.addActionListener(e -> showAbout());
        helpMenu.add(checkItem);
        helpMenu.add(aboutItem);
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
                } else if (trayIcon != null && !isVisible()) {
                    trayIcon.displayMessage(AppInfo.NAME + " " + availableRelease.version() + " is available",
                            "Open EVE Farm to update.", TrayIcon.MessageType.INFO);
                }
            }
        }.execute();
    }

    private void showUpdateDialog(ReleaseInfo release) {
        new AppUpdateDialog(this, appContext, release,
                () -> dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING))).setVisible(true);
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
                Desktop.getDesktop().browse(URI.create(AppInfo.HOME_PAGE));
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
        } catch (Exception ignored) {
            return null;
        }
    }

    private void setupTrayIcon(Image appIcon) {
        if (!SystemTray.isSupported() || appIcon == null) {
            return;
        }
        PopupMenu popup = new PopupMenu();
        MenuItem openItem = new MenuItem("Open");
        openItem.addActionListener(e -> restoreFromTray());
        MenuItem exit = new MenuItem("Exit");
        exit.addActionListener(e -> dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING)));
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
        setVisible(true);
        setExtendedState(getExtendedState() & ~JFrame.ICONIFIED);
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
        Component previouslySelected = tabbedPane.getTabCount() > 0 ? tabbedPane.getSelectedComponent() : null;

        tabbedPane.removeAll();
        Set<String> hidden = TabVisibility.readHidden(appContext.settingsDao);
        for (Map.Entry<String, TabSpec> entry : orderedTabSpecs().entrySet()) {
            if (!hidden.contains(entry.getKey())) {
                TabSpec spec = entry.getValue();
                tabbedPane.addTab(spec.label(), spec.icon(), spec.component());
            }
        }

        if (previouslySelected != null) {
            for (int i = 0; i < tabbedPane.getTabCount(); i++) {
                if (tabbedPane.getComponentAt(i) == previouslySelected) {
                    tabbedPane.setSelectedIndex(i);
                    break;
                }
            }
        }
    }

    private void installTabMenu() {
        tabbedPane.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                showTabMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                showTabMenu(e);
            }
        });
    }

    private void showTabMenu(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        int index = tabbedPane.indexAtLocation(e.getX(), e.getY());
        String key = index < 0 ? null : tabKey(tabbedPane.getComponentAt(index));
        if (key == null) {
            return;
        }
        JPopupMenu menu = new JPopupMenu();
        JMenuItem close = new JMenuItem("Close Tab", Icons.CANCEL);
        close.setEnabled(tabbedPane.getTabCount() > 1);
        close.addActionListener(event -> closeTab(key));
        JMenuItem showTabs = new JMenuItem("Show Tabs...", Icons.EYE);
        showTabs.addActionListener(this::showTabsItemActionPerformed);
        menu.add(close);
        menu.addSeparator();
        menu.add(showTabs);
        menu.show(tabbedPane, e.getX(), e.getY());
    }

    private String tabKey(Component component) {
        for (Map.Entry<String, TabSpec> entry : tabSpecs.entrySet()) {
            if (entry.getValue().component() == component) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void closeTab(String key) {
        Set<String> hidden = TabVisibility.readHidden(appContext.settingsDao);
        hidden.add(key);
        TabVisibility.writeHidden(appContext.settingsDao, hidden);
        rebuildTabs();
    }

    private void onCharactersChanged() {
        assetsPanel.refreshCharacterFilter();
        trackerPanel.refreshCharacterFilter();
        journalPanel.refreshCharacterFilter();
        marketOrdersPanel.refreshCharacterFilter();
        transactionsPanel.refreshCharacterFilter();
        contractsPanel.refreshCharacterFilter();
        industryJobsPanel.refreshCharacterFilter();
        valuesPanel.refreshCharacterFilter();
        lpStorePanel.refreshCharacterFilter();
        killsPanel.refreshCharacterFilter();
        abyssPanel.refreshCharacterFilter();
        miningPanel.refreshCharacterFilter();
        standingsPanel.refreshCharacterFilter();
        skillsPanel.refreshCharacterFilter();
        industryPanel.refreshCharacterFilter();
    }

    private Map<String, TabSpec> orderedTabSpecs() {
        List<String> savedOrder = TabVisibility.readOrder(appContext.settingsDao);
        Map<String, TabSpec> ordered = new LinkedHashMap<>();
        for (String key : savedOrder) {
            TabSpec spec = tabSpecs.get(key);
            if (spec != null) {
                ordered.put(key, spec);
            }
        }
        for (Map.Entry<String, TabSpec> entry : tabSpecs.entrySet()) {
            ordered.putIfAbsent(entry.getKey(), entry.getValue());
        }
        return ordered;
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
    private JTabbedPane tabbedPane;
    private JMenu updateMenu;
    private JMenuItem updateItem;

    private void initComponents() {

        tabbedPane = new JTabbedPane();
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
        charactersItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                charactersItemActionPerformed(evt);
            }
        });
        fileMenu.add(charactersItem);
        fileMenu.add(jSeparator1);

        exitItem.setText("Exit");
        exitItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                exitItemActionPerformed(evt);
            }
        });
        fileMenu.add(exitItem);

        jMenuBar1.add(fileMenu);

        updateMenu.setText("Update");

        updateItem.setText("Update...");
        updateItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                updateItemActionPerformed(evt);
            }
        });
        updateMenu.add(updateItem);

        jMenuBar1.add(updateMenu);

        optionsMenu.setText("Options");

        settingsItem.setText("Settings...");
        settingsItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                settingsItemActionPerformed(evt);
            }
        });
        optionsMenu.add(settingsItem);

        showTabsItem.setText("Show Tabs...");
        showTabsItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                showTabsItemActionPerformed(evt);
            }
        });
        optionsMenu.add(showTabsItem);
        optionsMenu.add(jSeparator2);

        backupItem.setText("Backup Data...");
        backupItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                backupItemActionPerformed(evt);
            }
        });
        optionsMenu.add(backupItem);

        restoreItem.setText("Restore Data...");
        restoreItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                restoreItemActionPerformed(evt);
            }
        });
        optionsMenu.add(restoreItem);

        jMenuBar1.add(optionsMenu);

        setJMenuBar(jMenuBar1);

        tabbedPane.setPreferredSize(new Dimension(1000, 650));
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(tabbedPane, BorderLayout.CENTER);
        getContentPane().add(LegalFooter.create(), BorderLayout.SOUTH);

        pack();
    }

    private void charactersItemActionPerformed(ActionEvent evt) {
        CharactersDialog dialog = new CharactersDialog(this, appContext, this::onCharactersChanged);
        dialog.setVisible(true);
    }

    private void settingsItemActionPerformed(ActionEvent evt) {
        SettingsDialog dialog = new SettingsDialog(this, appContext.settingsDao);
        dialog.setVisible(true);
    }

    private void showTabsItemActionPerformed(ActionEvent evt) {
        Map<String, String> labels = new LinkedHashMap<>();
        for (Map.Entry<String, TabSpec> entry : orderedTabSpecs().entrySet()) {
            labels.put(entry.getKey(), entry.getValue().label());
        }
        TabVisibilityDialog dialog = new TabVisibilityDialog(this, appContext.settingsDao, labels, this::rebuildTabs);
        dialog.setVisible(true);
    }

    private void backupItemActionPerformed(ActionEvent evt) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Backup Data");
        chooser.setSelectedFile(new File(BackupRestoreService.defaultBackupFileName()));
        chooser.setFileFilter(new FileNameExtensionFilter("EVE Farm database (*.db)", "db"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File target = chooser.getSelectedFile();
        if (!target.getName().toLowerCase(Locale.ROOT).endsWith(".db")) {
            target = new File(target.getParentFile(), target.getName() + ".db");
        }
        try {
            appContext.backupRestoreService.backupTo(target.toPath());
            JOptionPane.showMessageDialog(this, "Backup saved to:\n" + target,
                    "Backup Data", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Backup failed: " + e.getMessage(),
                    "Backup Data", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void restoreItemActionPerformed(ActionEvent evt) {
        int confirm = JOptionPane.showConfirmDialog(this,
                "Restoring will replace ALL current data with the chosen backup, and the app will "
                        + "close so the restore can be applied on next launch.\n\n"
                        + "Your current data is backed up automatically before the swap.\n\n"
                        + "Continue?",
                "Restore Data", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Restore Data");
        chooser.setFileFilter(new FileNameExtensionFilter("EVE Farm database (*.db)", "db"));
        File autoBackups = AppPaths.backupDir().toFile();
        if (autoBackups.isDirectory()) {
            chooser.setCurrentDirectory(autoBackups);
        }
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path source = chooser.getSelectedFile().toPath();

        String error = appContext.backupRestoreService.validateBackupFile(source);
        if (error != null) {
            JOptionPane.showMessageDialog(this, "Can't restore this file:\n" + error,
                    "Restore Data", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            appContext.backupRestoreService.stageRestore(source);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Failed to stage the restore: " + e.getMessage(),
                    "Restore Data", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JOptionPane.showMessageDialog(this,
                "Restore staged. The app will now close - reopen it to finish the restore.",
                "Restore Data", JOptionPane.INFORMATION_MESSAGE);
        dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
    }

    private void exitItemActionPerformed(ActionEvent evt) {
        dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
    }

    private void updateItemActionPerformed(ActionEvent evt) {
        UpdateDialog dialog = new UpdateDialog(this, appContext, this::onDataUpdated);
        dialog.setVisible(true);
    }

    private void onDataUpdated() {
        assetsPanel.onShown();
        trackerPanel.onShown();
        journalPanel.onShown();
        marketOrdersPanel.onShown();
        transactionsPanel.onShown();
        contractsPanel.onShown();
        industryJobsPanel.onShown();
        valuesPanel.onShown();
        lpStorePanel.onShown();
        killsPanel.onShown();
        miningPanel.onShown();
        standingsPanel.onShown();
        skillsPanel.onShown();
    }
}
