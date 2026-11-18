package com.evefarm.ui;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Component;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

final class TextReportSaver {

    private static final Logger LOG = Logger.getLogger(TextReportSaver.class.getName());

    static void save(Component parent, String title, String fileName, Supplier<List<String>> lines,
                     Consumer<Boolean> busy) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setSelectedFile(new File(fileName));
        chooser.setFileFilter(new FileNameExtensionFilter("Text file (*.txt)", "txt"));
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path target = withTxtExtension(chooser.getSelectedFile());
        if (Files.exists(target) && JOptionPane.showConfirmDialog(parent, target.getFileName()
                + " already exists. Replace it?", title, JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        busy.accept(true);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                Files.write(target, lines.get(), StandardCharsets.UTF_8);
                return null;
            }

            @Override
            protected void done() {
                busy.accept(false);
                try {
                    get();
                    JOptionPane.showMessageDialog(parent, "Report saved to:\n" + target, title,
                            JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Failed to save " + target, e);
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    JOptionPane.showMessageDialog(parent, "Couldn't save the report: " + cause.getMessage(), title,
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    static Path withTxtExtension(File chosen) {
        return chosen.getName().toLowerCase(Locale.ROOT).endsWith(".txt") ? chosen.toPath()
                : chosen.toPath().resolveSibling(chosen.getName() + ".txt");
    }

    private TextReportSaver() {
    }
}
