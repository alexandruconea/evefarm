package com.evefarm.ui;

import com.evefarm.service.UpdateCategories;
import com.evefarm.ui.DataUpdater.Category;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class UpdateDialog extends JDialog {

    private static final Dimension NOW_BUTTON_SIZE = new Dimension(100, 26);
    private static final Dimension BOTTOM_BUTTON_SIZE = new Dimension(130, 28);

    private record Row(Category category, JCheckBox box, JButton nowButton) {
    }

    private final DataUpdater updater;
    private final List<Row> rows = new ArrayList<>();
    private final Runnable refreshListener = this::refresh;
    private final Timer countdown = new Timer(1000, e -> refresh());

    private final JCheckBox allBox = new JCheckBox("All");
    private final JRadioButton firstCharacterRadio = new JRadioButton("First Character");
    private final JRadioButton allCharactersRadio = new JRadioButton("All Characters", true);
    private final JButton updateButton = new JButton("Update", Icons.REFRESH);

    UpdateDialog(Window owner, DataUpdater updater) {
        super(owner, "Update", ModalityType.APPLICATION_MODAL);
        this.updater = updater;
        buildUi();
        refresh();
        updater.addListener(refreshListener);
        countdown.start();
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

        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        for (Category category : updater.categories()) {
            list.add(row(category));
        }
        JCheckBox blueprints = new JCheckBox("Blueprints (not yet supported)");
        blueprints.setEnabled(false);
        JPanel blueprintsRow = new JPanel(new BorderLayout());
        blueprintsRow.add(blueprints, BorderLayout.CENTER);
        list.add(blueprintsRow);

        allBox.addActionListener(e -> {
            for (Row row : rows) {
                if (row.box().isEnabled()) {
                    row.box().setSelected(allBox.isSelected());
                }
            }
        });

        fixSize(updateButton, BOTTOM_BUTTON_SIZE);
        updateButton.setDisabledIcon(Icons.disabled(Icons.REFRESH));
        updateButton.setToolTipText("Updates the ticked lines in the background and closes this window. The round "
                + "button at the right of the tabs spins until it's done");
        updateButton.addActionListener(e -> runUpdate());
        JButton closeButton = new JButton("Close", Icons.CANCEL);
        fixSize(closeButton, BOTTOM_BUTTON_SIZE);
        closeButton.addActionListener(e -> dispose());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(updateButton);
        buttonPanel.add(closeButton);

        JScrollPane scrollPane = new JScrollPane(list);
        scrollPane.setPreferredSize(new Dimension(440, 300));

        setLayout(new BorderLayout());
        add(topRow, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private JPanel row(Category category) {
        JCheckBox box = new JCheckBox(category.label(), true);
        box.addActionListener(e -> syncAllBox());
        if (UpdateCategories.TRACKER.equals(category.key())) {
            box.setToolTipText("Wallet, net worth, skill points and the other values of the Tracker");
        }
        JButton nowButton = new JButton("Now", Icons.PLAY);
        fixSize(nowButton, NOW_BUTTON_SIZE);
        nowButton.setDisabledIcon(Icons.disabled(Icons.PLAY));
        nowButton.addActionListener(e -> updater.update(List.of(category.key()), firstCharacterRadio.isSelected()));
        rows.add(new Row(category, box, nowButton));

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

    private void refresh() {
        Instant now = Instant.now();
        boolean anyReady = false;
        for (Row row : rows) {
            boolean pending = updater.isPending(row.category());
            Instant readyAt = updater.readyAt(row.category());
            boolean ready = !pending && !now.isBefore(readyAt);
            row.box().setEnabled(ready);
            row.nowButton().setEnabled(ready);
            row.nowButton().setText(pending ? "Updating" : ready ? "Now"
                    : formatRemaining(Duration.between(now, readyAt)));
            anyReady |= ready;
        }
        allBox.setEnabled(anyReady);
        updateButton.setEnabled(anyReady);
        syncAllBox();
    }

    private void syncAllBox() {
        allBox.setSelected(allTicked(rows.stream().map(Row::box).toList()));
    }

    static boolean allTicked(List<JCheckBox> boxes) {
        List<JCheckBox> enabled = boxes.stream().filter(JCheckBox::isEnabled).toList();
        return !enabled.isEmpty() && enabled.stream().allMatch(JCheckBox::isSelected);
    }

    static String formatRemaining(Duration duration) {
        long totalSeconds = Math.max(0, duration.getSeconds());
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format(Locale.US, "%dh %02dm", hours, minutes);
        }
        if (minutes > 0) {
            return String.format(Locale.US, "%dm %02ds", minutes, seconds);
        }
        return String.format(Locale.US, "%ds", seconds);
    }

    private void runUpdate() {
        List<String> keys = rows.stream()
                .filter(row -> row.box().isSelected() && row.box().isEnabled())
                .map(row -> row.category().key())
                .toList();
        updater.update(keys, firstCharacterRadio.isSelected());
        dispose();
    }

    @Override
    public void dispose() {
        countdown.stop();
        updater.removeListener(refreshListener);
        super.dispose();
    }
}
