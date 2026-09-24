package com.evefarm;

import com.evefarm.auth.TokenCipher;
import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.service.BackupRestoreService;
import com.evefarm.ui.CompactMenuItemUI;
import com.evefarm.ui.MainFrame;
import com.evefarm.ui.UnderlineTabbedPaneUI;
import com.evefarm.util.AppLogging;
import com.evefarm.util.AppPaths;
import com.evefarm.util.SingleInstance;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class Main {

    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {
        SingleInstance instance = null;
        try {
            Optional<SingleInstance> acquired = SingleInstance.acquire(AppPaths.appDataDir());
            if (acquired.isEmpty()) {
                if (!SingleInstance.signalRunningInstance(AppPaths.appDataDir())) {
                    showMessage("EVE Farm is already running - look for its icon in the system tray.",
                            JOptionPane.INFORMATION_MESSAGE);
                }
                return;
            }
            instance = acquired.get();
        } catch (IOException e) {
            System.err.println("Couldn't check for another running EVE Farm: " + e.getMessage());
        }

        AppLogging.init();
        try {
            startApp(instance);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Fatal error during startup", e);
            showMessage("EVE Farm couldn't start:\n" + describe(e) + "\n\nDetails are in the log folder:\n"
                    + AppPaths.appDataDir().resolve("logs"), JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    private static void showMessage(String message, int type) {
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println(message);
            return;
        }
        JOptionPane.showMessageDialog(null, message, "EVE Farm", type);
    }

    private static String describe(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String top = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        return root == error || root.getMessage() == null ? top : top + "\n(" + root.getMessage() + ")";
    }

    private record OpenedData(Database database, AppContext appContext) {
    }

    private static OpenedData openData() {
        Database database = new Database();
        try {
            MigrationRunner.run(database);
            AppContext appContext = new AppContext(database);
            appContext.tokenDao.migrateLegacyPlaintextTokens();
            return new OpenedData(database, appContext);
        } catch (RuntimeException e) {
            database.close();
            throw e;
        }
    }

    private static void startApp(SingleInstance instance) throws IOException {
        Optional<Path> databaseBeforeRestore = BackupRestoreService.applyPendingRestoreIfAny();

        OpenedData data;
        boolean restoreUndone = false;
        try {
            data = openData();
        } catch (RuntimeException e) {
            if (databaseBeforeRestore.isEmpty()) {
                throw e;
            }
            LOG.log(Level.SEVERE, "The restored database couldn't be opened", e);
            BackupRestoreService.undoRestore(databaseBeforeRestore.get());
            data = openData();
            restoreUndone = true;
        }
        Database database = data.database();
        AppContext appContext = data.appContext();
        boolean showRestoreUndone = restoreUndone;

        installLookAndFeel(appContext.settingsDao.getOrDefault(SettingsDao.LAF_THEME, SettingsDao.DEFAULT_LAF_THEME));
        appContext.schedulerService.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            appContext.schedulerService.stop();
            database.close();
        }, "shutdown"));

        SwingUtilities.invokeLater(() -> {
            warnIfTokensUnprotected();
            MainFrame frame = new MainFrame(appContext);
            frame.setVisible(true);
            if (showRestoreUndone) {
                JOptionPane.showMessageDialog(frame,
                        "The backup you restored couldn't be opened, so EVE Farm put your previous data back.\n\n"
                                + "Details are in the log folder:\n" + AppPaths.appDataDir().resolve("logs"),
                        "Restore Data", JOptionPane.WARNING_MESSAGE);
            }
            if (instance != null) {
                instance.onShowRequested(() -> SwingUtilities.invokeLater(frame::bringToFront));
            }
        });
    }

    private static void warnIfTokensUnprotected() {
        if (TokenCipher.isProtectionAvailable()) {
            return;
        }
        JOptionPane.showMessageDialog(null,
                "Windows DPAPI isn't available on this system. EVE Farm will not store or read login tokens "
                        + "or API keys in plaintext.\n\nAuthenticated features require the packaged Windows application.",
                "Secret Storage Unavailable", JOptionPane.WARNING_MESSAGE);
    }

    private static void installLookAndFeel(String theme) {
        try {
            FlatLaf.registerCustomDefaultsSource("com.evefarm.ui.theme");
            if ("flatlaf-dark".equals(theme)) {
                UIManager.setLookAndFeel(new FlatDarkLaf());
            } else {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                CompactMenuItemUI.install();
                UnderlineTabbedPaneUI.install();
            }
            boolean flat = UIManager.getLookAndFeel() instanceof FlatLaf;
            JFrame.setDefaultLookAndFeelDecorated(flat);
            JDialog.setDefaultLookAndFeelDecorated(flat);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to install look and feel '" + theme + "', falling back to default", e);
        }
    }

    private Main() {
    }
}
