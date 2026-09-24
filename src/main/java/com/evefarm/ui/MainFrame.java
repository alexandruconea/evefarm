package com.evefarm.ui;

import com.evefarm.AppContext;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.update.ReleaseInfo;
import com.evefarm.update.UpdateInstaller;
import com.evefarm.util.AppInfo;
import com.evefarm.util.AppPaths;

import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import java.awt.AWTException;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class MainFrame extends javax.swing.JFrame {

    private static final Logger LOG = Logger.getLogger(MainFrame.class.getName());

    private record TabSpec(String label, Icon icon, Component component) {
    }

    private static final int FIRST_UPDATE_CHECK_DELAY_MILLIS = 30_000;
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
        tabSpecs.put("agents", new TabSpec("Agents", Icons.MEDAL, agentsPanel));

        rebuildTabs();

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
            }
        });

        restoreWindowState();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                saveWindowState();
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
            javax.swing.SwingUtilities.invokeLater(() -> charactersItemActionPerformed(null));
        }

        setupUpdates();
        setupBeltKillAlerts();
        appContext.schedulerService.addSnapshotListener(() -> SwingUtilities.invokeLater(this::onDataUpdated));
    }

    private void setupBeltKillAlerts() {
        Runnable check = () -> {
            try {
                appContext.beltKillMilestoneService.checkForNewMilestone().ifPresent(milestone ->
                        SwingUtilities.invokeLater(() -> showTrayNotification("Belt hunting milestone",
                                String.format("Your characters have killed %,d NPCs in asteroid belts.", milestone))));
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
        checkItem.addActionListener(e -> checkForUpdates(true));
        JMenuItem aboutItem = new JMenuItem("About " + AppInfo.NAME, Icons.DOCUMENT);
        aboutItem.addActionListener(e -> showAbout());
        helpMenu.add(checkItem);
        helpMenu.add(aboutItem);
        jMenuBar1.add(helpMenu);

        updateAvailableButton.putClientProperty("JButton.buttonType", "toolBarButton");
        updateAvailableButton.setIcon(Icons.REFRESH);
        updateAvailableButton.setFocusable(false);
        updateAvailableButton.setVisible(false);
        updateAvailableButton.addActionListener(e -> {
            if (availableRelease != null) {
                showUpdateDialog(availableRelease);
            }
        });
        jMenuBar1.add(Box.createHorizontalGlue());
        jMenuBar1.add(updateAvailableButton);
        if (!(javax.swing.UIManager.getLookAndFeel() instanceof com.formdev.flatlaf.FlatLaf)) {
            WindowChrome.install(this, jMenuBar1);
        }

        announceIfJustUpdated();
        Thread cleanup = new Thread(UpdateInstaller::cleanUpAfterUpdate, "update-cleanup");
        cleanup.setDaemon(true);
        cleanup.start();

        if (!AppInfo.isDevBuild()) {
            Timer timer = new Timer(UPDATE_CHECK_INTERVAL_MILLIS, e -> checkForUpdates(false));
            timer.setInitialDelay(FIRST_UPDATE_CHECK_DELAY_MILLIS);
            timer.start();
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
                                + com.evefarm.util.AppPaths.appDataDir().resolve("logs").resolve("updater.log"),
                        "Update Failed", JOptionPane.WARNING_MESSAGE)));
    }

    private void checkForUpdates(boolean userAsked) {
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
                if (userAsked) {
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
                java.awt.Desktop.getDesktop().browse(java.net.URI.create(AppInfo.HOME_PAGE));
            } catch (Exception e) {
                LOG.log(Level.INFO, "Couldn't open the GitHub page", e);
            }
        }
    }

    private Image loadAppIcon() {
        try (java.io.InputStream in = getClass().getResourceAsStream("/icons/app-icon.png")) {
            if (in == null) {
                return null;
            }
            Image icon = javax.imageio.ImageIO.read(in);
            if (icon == null) {
                return null;
            }
            setIconImage(icon);
            if (java.awt.Taskbar.isTaskbarSupported()) {
                java.awt.Taskbar taskbar = java.awt.Taskbar.getTaskbar();
                if (taskbar.isSupported(java.awt.Taskbar.Feature.ICON_IMAGE)) {
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

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JMenuItem charactersItem;
    private javax.swing.JMenuItem exitItem;
    private javax.swing.JMenu fileMenu;
    private javax.swing.JMenuBar jMenuBar1;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JMenu optionsMenu;
    private javax.swing.JMenuItem settingsItem;
    private javax.swing.JMenuItem showTabsItem;
    private javax.swing.JSeparator jSeparator2;
    private javax.swing.JMenuItem backupItem;
    private javax.swing.JMenuItem restoreItem;
    private javax.swing.JTabbedPane tabbedPane;
    private javax.swing.JMenu updateMenu;
    private javax.swing.JMenuItem updateItem;
    // End of variables declaration//GEN-END:variables

    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        tabbedPane = new javax.swing.JTabbedPane();
        jMenuBar1 = new javax.swing.JMenuBar();
        fileMenu = new javax.swing.JMenu();
        charactersItem = new javax.swing.JMenuItem();
        jSeparator1 = new javax.swing.JSeparator();
        exitItem = new javax.swing.JMenuItem();
        updateMenu = new javax.swing.JMenu();
        updateItem = new javax.swing.JMenuItem();
        optionsMenu = new javax.swing.JMenu();
        settingsItem = new javax.swing.JMenuItem();
        showTabsItem = new javax.swing.JMenuItem();
        jSeparator2 = new javax.swing.JSeparator();
        backupItem = new javax.swing.JMenuItem();
        restoreItem = new javax.swing.JMenuItem();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("EVE Farm");
        setMinimumSize(new java.awt.Dimension(1000, 650));

        fileMenu.setText("File");

        charactersItem.setText("Characters...");
        charactersItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                charactersItemActionPerformed(evt);
            }
        });
        fileMenu.add(charactersItem);
        fileMenu.add(jSeparator1);

        exitItem.setText("Exit");
        exitItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exitItemActionPerformed(evt);
            }
        });
        fileMenu.add(exitItem);

        jMenuBar1.add(fileMenu);

        updateMenu.setText("Update");

        updateItem.setText("Update...");
        updateItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                updateItemActionPerformed(evt);
            }
        });
        updateMenu.add(updateItem);

        jMenuBar1.add(updateMenu);

        optionsMenu.setText("Options");

        settingsItem.setText("Settings...");
        settingsItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                settingsItemActionPerformed(evt);
            }
        });
        optionsMenu.add(settingsItem);

        showTabsItem.setText("Show Tabs...");
        showTabsItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showTabsItemActionPerformed(evt);
            }
        });
        optionsMenu.add(showTabsItem);
        optionsMenu.add(jSeparator2);

        backupItem.setText("Backup Data...");
        backupItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                backupItemActionPerformed(evt);
            }
        });
        optionsMenu.add(backupItem);

        restoreItem.setText("Restore Data...");
        restoreItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                restoreItemActionPerformed(evt);
            }
        });
        optionsMenu.add(restoreItem);

        jMenuBar1.add(optionsMenu);

        setJMenuBar(jMenuBar1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(tabbedPane, javax.swing.GroupLayout.DEFAULT_SIZE, 1000, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(tabbedPane, javax.swing.GroupLayout.DEFAULT_SIZE, 650, Short.MAX_VALUE)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void charactersItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_charactersItemActionPerformed
        CharactersDialog dialog = new CharactersDialog(this, appContext, this::onCharactersChanged);
        dialog.setVisible(true);
    }//GEN-LAST:event_charactersItemActionPerformed

    private void settingsItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_settingsItemActionPerformed
        SettingsDialog dialog = new SettingsDialog(this, appContext.settingsDao);
        dialog.setVisible(true);
    }//GEN-LAST:event_settingsItemActionPerformed

    private void showTabsItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showTabsItemActionPerformed
        Map<String, String> labels = new LinkedHashMap<>();
        for (Map.Entry<String, TabSpec> entry : orderedTabSpecs().entrySet()) {
            labels.put(entry.getKey(), entry.getValue().label());
        }
        TabVisibilityDialog dialog = new TabVisibilityDialog(this, appContext.settingsDao, labels, this::rebuildTabs);
        dialog.setVisible(true);
    }//GEN-LAST:event_showTabsItemActionPerformed

    private void backupItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_backupItemActionPerformed
        javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
        chooser.setDialogTitle("Backup Data");
        chooser.setSelectedFile(new java.io.File(com.evefarm.service.BackupRestoreService.defaultBackupFileName()));
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("EVE Farm database (*.db)", "db"));
        if (chooser.showSaveDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) {
            return;
        }
        java.io.File target = chooser.getSelectedFile();
        if (!target.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".db")) {
            target = new java.io.File(target.getParentFile(), target.getName() + ".db");
        }
        try {
            appContext.backupRestoreService.backupTo(target.toPath());
            javax.swing.JOptionPane.showMessageDialog(this, "Backup saved to:\n" + target,
                    "Backup Data", javax.swing.JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            javax.swing.JOptionPane.showMessageDialog(this, "Backup failed: " + e.getMessage(),
                    "Backup Data", javax.swing.JOptionPane.ERROR_MESSAGE);
        }
    }//GEN-LAST:event_backupItemActionPerformed

    private void restoreItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_restoreItemActionPerformed
        int confirm = javax.swing.JOptionPane.showConfirmDialog(this,
                "Restoring will replace ALL current data with the chosen backup, and the app will "
                        + "close so the restore can be applied on next launch.\n\n"
                        + "Your current data is backed up automatically before the swap.\n\n"
                        + "Continue?",
                "Restore Data", javax.swing.JOptionPane.YES_NO_OPTION, javax.swing.JOptionPane.WARNING_MESSAGE);
        if (confirm != javax.swing.JOptionPane.YES_OPTION) {
            return;
        }

        javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
        chooser.setDialogTitle("Restore Data");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("EVE Farm database (*.db)", "db"));
        java.io.File autoBackups = com.evefarm.util.AppPaths.backupDir().toFile();
        if (autoBackups.isDirectory()) {
            chooser.setCurrentDirectory(autoBackups);
        }
        if (chooser.showOpenDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) {
            return;
        }
        java.nio.file.Path source = chooser.getSelectedFile().toPath();

        String error = appContext.backupRestoreService.validateBackupFile(source);
        if (error != null) {
            javax.swing.JOptionPane.showMessageDialog(this, "Can't restore this file:\n" + error,
                    "Restore Data", javax.swing.JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            appContext.backupRestoreService.stageRestore(source);
        } catch (Exception e) {
            javax.swing.JOptionPane.showMessageDialog(this, "Failed to stage the restore: " + e.getMessage(),
                    "Restore Data", javax.swing.JOptionPane.ERROR_MESSAGE);
            return;
        }

        javax.swing.JOptionPane.showMessageDialog(this,
                "Restore staged. The app will now close - reopen it to finish the restore.",
                "Restore Data", javax.swing.JOptionPane.INFORMATION_MESSAGE);
        dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
    }//GEN-LAST:event_restoreItemActionPerformed

    private void exitItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exitItemActionPerformed
        dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
    }//GEN-LAST:event_exitItemActionPerformed

    private void updateItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_updateItemActionPerformed
        UpdateDialog dialog = new UpdateDialog(this, appContext, this::onDataUpdated);
        dialog.setVisible(true);
    }//GEN-LAST:event_updateItemActionPerformed

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
    }
}
