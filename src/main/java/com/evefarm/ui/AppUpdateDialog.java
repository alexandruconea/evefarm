package com.evefarm.ui;

import com.evefarm.AppContext;
import com.evefarm.update.ReleaseInfo;
import com.evefarm.update.UpdateInstaller;
import com.evefarm.util.AppInfo;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

final class AppUpdateDialog extends JDialog {

    private static final Logger LOG = Logger.getLogger(AppUpdateDialog.class.getName());

    private record Step(String text, long done, long total) {
    }

    private final AppContext appContext;
    private final ReleaseInfo release;
    private final Runnable exitApp;
    private final Optional<Path> installDir = UpdateInstaller.installDirectory();
    private final JButton updateButton = new JButton();
    private final JButton pageButton = new JButton("Release Page");
    private final JButton skipButton = new JButton("Skip This Version");
    private final JButton laterButton = new JButton("Later");
    private final JProgressBar progressBar = new JProgressBar();
    private final JLabel statusLabel = new JLabel(" ");

    AppUpdateDialog(Frame owner, AppContext appContext, ReleaseInfo release, Runnable exitApp) {
        super(owner, "Update " + AppInfo.NAME, ModalityType.APPLICATION_MODAL);
        this.appContext = appContext;
        this.release = release;
        this.exitApp = exitApp;
        buildUi();
        pack();
        setLocationRelativeTo(owner);
    }

    private boolean canInstallHere() {
        return installDir.isPresent() && release.zipUrl() != null && release.signatureUrl() != null;
    }

    private void buildUi() {
        JLabel header = new JLabel("<html><b>" + AppInfo.NAME + " " + release.version() + " is available.</b>"
                + "<br>You have " + AppInfo.version() + ". Your data isn't touched by updating, and it's backed up "
                + "first anyway.</html>");

        JTextArea notes = new JTextArea(release.notes().isBlank() ? "No release notes." : release.notes());
        TextAreaStyler.scrollable(notes);
        notes.setCaretPosition(0);
        JScrollPane notesScroll = new JScrollPane(notes);
        notesScroll.setPreferredSize(new Dimension(560, 260));

        progressBar.setVisible(false);
        JPanel progress = new JPanel(new BorderLayout(0, 4));
        progress.add(progressBar, BorderLayout.NORTH);
        progress.add(statusLabel, BorderLayout.SOUTH);

        updateButton.setText(canInstallHere() ? "Update Now" : "Download");
        updateButton.setIcon(Icons.REFRESH);
        updateButton.addActionListener(e -> updateNow());
        pageButton.addActionListener(e -> openReleasePage());
        skipButton.addActionListener(e -> {
            appContext.updateService.skip(release);
            dispose();
        });
        laterButton.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(updateButton);
        buttons.add(pageButton);
        buttons.add(skipButton);
        buttons.add(laterButton);

        JPanel south = new JPanel(new BorderLayout(0, 8));
        south.add(progress, BorderLayout.NORTH);
        south.add(buttons, BorderLayout.SOUTH);

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(header, BorderLayout.NORTH);
        content.add(notesScroll, BorderLayout.CENTER);
        content.add(south, BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setDefaultButton(updateButton);
    }

    private void openReleasePage() {
        try {
            Desktop.getDesktop().browse(URI.create(release.pageUrl()));
        } catch (Exception e) {
            LOG.log(Level.INFO, "Couldn't open the release page", e);
        }
    }

    private void updateNow() {
        if (!canInstallHere()) {
            openReleasePage();
            dispose();
            return;
        }
        setButtonsEnabled(false);
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);
        pack();
        new SwingWorker<UpdateInstaller.PreparedUpdate, Step>() {
            @Override
            protected UpdateInstaller.PreparedUpdate doInBackground() throws Exception {
                return appContext.updateInstaller.prepare(release, installDir.get(),
                        (text, done, total) -> publish(new Step(text, done, total)));
            }

            @Override
            protected void process(List<Step> steps) {
                Step step = steps.get(steps.size() - 1);
                if (step.total() > 0) {
                    progressBar.setIndeterminate(false);
                    progressBar.setMaximum(1000);
                    progressBar.setValue((int) (step.done() * 1000 / step.total()));
                    statusLabel.setText(step.text() + " - " + (step.done() / (1024 * 1024)) + " of "
                            + (step.total() / (1024 * 1024)) + " MB");
                } else {
                    progressBar.setIndeterminate(true);
                    statusLabel.setText(step.text() + "...");
                }
            }

            @Override
            protected void done() {
                try {
                    UpdateInstaller.PreparedUpdate prepared = get();
                    statusLabel.setText("Restarting into " + release.version() + "...");
                    appContext.updateInstaller.launchSwap(prepared);
                    exitApp.run();
                } catch (Exception e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    LOG.log(Level.WARNING, "Update to " + release.version() + " failed", cause);
                    progressBar.setVisible(false);
                    statusLabel.setText(" ");
                    setButtonsEnabled(true);
                    JOptionPane.showMessageDialog(AppUpdateDialog.this,
                            "The update couldn't be installed:\n" + cause.getMessage()
                                    + "\n\nNothing was changed. You can try again or download it from the release page.",
                            "Update " + AppInfo.NAME, JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void setButtonsEnabled(boolean enabled) {
        updateButton.setEnabled(enabled);
        pageButton.setEnabled(enabled);
        skipButton.setEnabled(enabled);
        laterButton.setEnabled(enabled);
        setDefaultCloseOperation(enabled ? DISPOSE_ON_CLOSE : DO_NOTHING_ON_CLOSE);
    }
}
