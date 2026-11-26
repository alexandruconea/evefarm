package com.evefarm.ui;

import com.evefarm.AppContext;
import com.evefarm.service.BackupRestoreService;
import com.evefarm.util.AppPaths;
import com.evefarm.util.Text;
import com.evefarm.util.WrongPasswordException;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

final class BackupRestoreFlow {

    private static final String BACKUP_TITLE = "Backup Data";
    private static final String RESTORE_TITLE = "Restore Data";

    private final JFrame owner;
    private final AppContext appContext;
    private final Consumer<Boolean> setMenusEnabled;
    private final Runnable exitApplication;

    private record RestoreCheck(String error, BackupRestoreService.PasswordNeeds needs) {
    }

    BackupRestoreFlow(JFrame owner, AppContext appContext, Consumer<Boolean> setMenusEnabled,
                      Runnable exitApplication) {
        this.owner = owner;
        this.appContext = appContext;
        this.setMenusEnabled = setMenusEnabled;
        this.exitApplication = exitApplication;
    }

    void backup() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(BACKUP_TITLE);
        chooser.setSelectedFile(new File(BackupRestoreService.defaultBackupFileName()));
        chooser.setFileFilter(new FileNameExtensionFilter("EVE Farm database (*.db)", "db"));
        if (chooser.showSaveDialog(owner) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File target = chooser.getSelectedFile();
        if (!target.getName().toLowerCase(Locale.ROOT).endsWith(".db")) {
            target = new File(target.getParentFile(), target.getName() + ".db");
        }
        Path destination = target.toPath();
        setMenusEnabled.accept(false);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                appContext.backupRestoreService.backupTo(destination);
                return null;
            }

            @Override
            protected void done() {
                setMenusEnabled.accept(true);
                try {
                    get();
                    JOptionPane.showMessageDialog(owner, "Backup saved to:\n" + destination,
                            BACKUP_TITLE, JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    JOptionPane.showMessageDialog(owner, "Backup failed: " + cause.getMessage(),
                            BACKUP_TITLE, JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    void restore() {
        int confirm = JOptionPane.showConfirmDialog(owner,
                "Restoring will replace ALL current data with the chosen backup, and the app will "
                        + "close so the restore can be applied on next launch.\n\n"
                        + "Your current data is backed up automatically before the swap.\n\n"
                        + "Continue?",
                RESTORE_TITLE, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(RESTORE_TITLE);
        chooser.setFileFilter(new FileNameExtensionFilter("EVE Farm database (*.db)", "db"));
        File autoBackups = AppPaths.backupDir().toFile();
        if (autoBackups.isDirectory()) {
            chooser.setCurrentDirectory(autoBackups);
        }
        if (chooser.showOpenDialog(owner) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path source = chooser.getSelectedFile().toPath();
        setMenusEnabled.accept(false);
        new SwingWorker<RestoreCheck, Void>() {
            @Override
            protected RestoreCheck doInBackground() {
                String error = appContext.backupRestoreService.validateBackupFile(source);
                return error != null ? new RestoreCheck(error, null)
                        : new RestoreCheck(null, appContext.backupRestoreService.passwordNeeds(source));
            }

            @Override
            protected void done() {
                RestoreCheck check;
                try {
                    check = get();
                } catch (Exception e) {
                    restoreFailed(e);
                    return;
                }
                if (check.error() != null) {
                    setMenusEnabled.accept(true);
                    JOptionPane.showMessageDialog(owner, "Can't restore this file:\n" + check.error(),
                            RESTORE_TITLE, JOptionPane.ERROR_MESSAGE);
                } else if (check.needs().any()) {
                    askBackupPassword(source, check.needs(), false);
                } else {
                    stageRestore(source, null, check.needs());
                }
            }
        }.execute();
    }

    private void askBackupPassword(Path source, BackupRestoreService.PasswordNeeds needs, boolean wrongBefore) {
        List<String> kept = new ArrayList<>();
        if (!needs.characters().isEmpty()) {
            kept.add("the logins of " + String.join(", ", needs.characters()));
        }
        if (needs.janiceKey()) {
            kept.add("the Janice API key");
        }
        String text = (wrongBefore ? "That password doesn't open this backup's logins. " : "")
                + "This backup keeps " + String.join(" and ", kept) + " protected with a backup password. Type "
                + "it to restore them, or restore without them and add those characters again later.";
        JPasswordField password = new JPasswordField(24);
        password.addAncestorListener(new AncestorListener() {
            @Override
            public void ancestorAdded(AncestorEvent event) {
                SwingUtilities.invokeLater(password::requestFocusInWindow);
            }

            @Override
            public void ancestorRemoved(AncestorEvent event) {
            }

            @Override
            public void ancestorMoved(AncestorEvent event) {
            }
        });
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.add(new JLabel("<html><body style=\"width: 340px\">" + Text.escapeHtml(text) + "</body></html>"),
                BorderLayout.NORTH);
        panel.add(password, BorderLayout.CENTER);
        Object[] options = {"Restore Logins", "Restore Without Them", "Cancel"};
        int choice = JOptionPane.showOptionDialog(owner, panel, RESTORE_TITLE, JOptionPane.DEFAULT_OPTION,
                wrongBefore ? JOptionPane.WARNING_MESSAGE : JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        if (choice == 0 && password.getPassword().length > 0) {
            stageRestore(source, new String(password.getPassword()), needs);
        } else if (choice == 0) {
            askBackupPassword(source, needs, false);
        } else if (choice == 1) {
            stageRestore(source, null, needs);
        } else {
            setMenusEnabled.accept(true);
        }
    }

    private void stageRestore(Path source, String password, BackupRestoreService.PasswordNeeds needs) {
        new SwingWorker<BackupRestoreService.Staged, Void>() {
            @Override
            protected BackupRestoreService.Staged doInBackground() throws Exception {
                return appContext.backupRestoreService.stageRestore(source, password);
            }

            @Override
            protected void done() {
                BackupRestoreService.Staged staged;
                try {
                    staged = get();
                } catch (Exception e) {
                    if (e.getCause() instanceof WrongPasswordException) {
                        askBackupPassword(source, needs, true);
                    } else {
                        restoreFailed(e);
                    }
                    return;
                }
                setMenusEnabled.accept(true);
                JOptionPane.showMessageDialog(owner, stagedMessage(staged), RESTORE_TITLE,
                        JOptionPane.INFORMATION_MESSAGE);
                exitApplication.run();
            }
        }.execute();
    }

    static String stagedMessage(BackupRestoreService.Staged staged) {
        String gamelogs = staged.gamelogsRestored() == 0 ? ""
                : "\n\n" + Text.count(staged.gamelogsRestored()) + " Gamelog"
                + (staged.gamelogsRestored() == 1 ? " was" : "s were") + " put back in "
                + staged.gamelogDirectory() + ".";
        String addAgain = staged.toAddAgain().isEmpty() ? ""
                : "\n\nAfter it opens, add these characters again (File > Characters... > Add Character...): "
                + String.join(", ", staged.toAddAgain()) + ". Their history is kept.";
        return "Restore staged. The app will now close - reopen it to finish the restore." + gamelogs + addAgain;
    }

    private void restoreFailed(Exception e) {
        setMenusEnabled.accept(true);
        Throwable cause = e.getCause() == null ? e : e.getCause();
        JOptionPane.showMessageDialog(owner, "Failed to stage the restore: " + cause.getMessage(),
                RESTORE_TITLE, JOptionPane.ERROR_MESSAGE);
    }
}
