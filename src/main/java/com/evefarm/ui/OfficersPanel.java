package com.evefarm.ui;

import com.evefarm.AppContext;
import com.evefarm.model.KillDayTypeRow;
import com.evefarm.model.OfficerDrop;
import com.evefarm.model.OfficerSighting;
import com.evefarm.model.SpawnMember;
import com.evefarm.service.NpcCatalog;
import com.evefarm.util.DateUtil;
import com.evefarm.util.IskFormatter;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class OfficersPanel extends JPanel {

    private static final Logger LOG = Logger.getLogger(OfficersPanel.class.getName());

    private final AppContext appContext;
    private final OfficersTableModel officersModel = new OfficersTableModel();
    private final JTable officersTable = new JTable(officersModel);
    private final SpawnTableModel spawnModel = new SpawnTableModel();
    private final JTable spawnTable = new JTable(spawnModel);
    private final DropsTableModel dropsModel = new DropsTableModel();
    private final JTable dropsTable = new JTable(dropsModel);
    private final JButton scanButton = new JButton("Scan Gamelogs", Icons.REFRESH);
    private final JLabel summaryLabel = new JLabel(" ");
    private final JLabel headerLabel = new JLabel(" ");
    private final JTextArea timingText = wrappingText();
    private final JTextArea payoutText = wrappingText();
    private final JButton addDropButton = new JButton("Add...", Icons.ADD);
    private final JButton removeDropButton = new JButton("Remove", Icons.REMOVE);
    private final JLabel dropsTotalLabel = new JLabel(" ");
    private final DefaultComboBoxModel<String> beltModel = new DefaultComboBoxModel<>();
    private final JComboBox<String> beltCombo = new JComboBox<>(beltModel);
    private final JTextField notesField = new JTextField(36);
    private final JButton saveButton = new JButton("Save", Icons.SAVE);
    private final AtomicInteger listGeneration = new AtomicInteger();
    private final AtomicInteger detailGeneration = new AtomicInteger();
    private final AtomicInteger dropsGeneration = new AtomicInteger();

    private OfficerSighting selected;
    private boolean applyingRows;

    public OfficersPanel(AppContext appContext) {
        this.appContext = appContext;
        buildUi();
        showDetail(null);
    }

    public void onShown() {
        reload();
    }

    private void buildUi() {
        setLayout(new BorderLayout());

        scanButton.addActionListener(e -> scanGamelogs());
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        toolbar.add(scanButton);
        toolbar.add(summaryLabel);
        add(toolbar, BorderLayout.NORTH);

        officersTable.setRowSorter(new TableRowSorter<>(officersModel));
        officersTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        officersTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !applyingRows) {
                int viewRow = officersTable.getSelectedRow();
                showDetail(viewRow < 0 ? null
                        : officersModel.rowAt(officersTable.convertRowIndexToModel(viewRow)));
            }
        });
        TableStyler.style(officersTable);
        spawnTable.setRowSorter(new TableRowSorter<>(spawnModel));
        TableStyler.style(spawnTable);
        dropsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        dropsTable.getSelectionModel().addListSelectionListener(e ->
                removeDropButton.setEnabled(selected != null && dropsTable.getSelectedRow() >= 0));
        TableStyler.style(dropsTable);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(officersTable), buildDetailPanel());
        split.setResizeWeight(0.4);
        split.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
        add(split, BorderLayout.CENTER);
    }

    private JPanel buildDetailPanel() {
        headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD, headerLabel.getFont().getSize2D() + 2f));

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        addLeftAligned(header, headerLabel);
        header.add(Box.createVerticalStrut(4));
        addLeftAligned(header, timingText);
        header.add(Box.createVerticalStrut(2));
        addLeftAligned(header, payoutText);

        beltCombo.setEditable(true);
        beltCombo.setPreferredSize(new Dimension(280, beltCombo.getPreferredSize().height));
        saveButton.addActionListener(e -> saveDetails());
        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        form.add(new JLabel("Belt:"));
        form.add(beltCombo);
        form.add(new JLabel("Notes:"));
        form.add(notesField);
        form.add(saveButton);

        JPanel spawnPanel = new JPanel(new BorderLayout(0, 4));
        spawnPanel.add(sectionHeader("Spawn"), BorderLayout.NORTH);
        spawnPanel.add(new JScrollPane(spawnTable), BorderLayout.CENTER);

        JSplitPane spawnAndDrops = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, spawnPanel, buildDropsPanel());
        spawnAndDrops.setResizeWeight(0.7);
        spawnAndDrops.setBorder(null);

        JPanel detail = new JPanel(new BorderLayout(0, 6));
        detail.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        detail.add(header, BorderLayout.NORTH);
        detail.add(spawnAndDrops, BorderLayout.CENTER);
        detail.add(form, BorderLayout.SOUTH);
        return detail;
    }

    private JPanel buildDropsPanel() {
        addDropButton.addActionListener(e -> addDrop());
        removeDropButton.addActionListener(e -> removeDrop());
        for (JButton button : List.of(addDropButton, removeDropButton)) {
            button.putClientProperty("JButton.buttonType", "toolBarButton");
            button.setFocusable(false);
        }
        addDropButton.setToolTipText("Record an item this officer dropped");
        removeDropButton.setToolTipText("Remove the selected drop");

        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.add(sectionHeader("Drops", dropsTotalLabel, addDropButton, removeDropButton), BorderLayout.NORTH);
        panel.add(new JScrollPane(dropsTable), BorderLayout.CENTER);
        return panel;
    }

    private static JPanel sectionHeader(String title, JComponent... trailing) {
        JLabel label = new JLabel(title);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        for (JComponent component : trailing) {
            right.add(component);
        }
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.add(label, BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);
        header.setPreferredSize(new Dimension(10, 28));
        return header;
    }

    private static JTextArea wrappingText() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setOpaque(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(null);
        area.setFont(new JLabel().getFont());
        return area;
    }

    private static void addLeftAligned(JPanel panel, JComponent component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(component);
    }

    private void reload() {
        int generation = listGeneration.incrementAndGet();
        new SwingWorker<List<OfficerSighting>, Void>() {
            @Override
            protected List<OfficerSighting> doInBackground() {
                return appContext.officerService.listSightings();
            }

            @Override
            protected void done() {
                if (generation != listGeneration.get()) {
                    return;
                }
                try {
                    applySightings(get());
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Failed to load officer sightings", e);
                }
            }
        }.execute();
    }

    private void applySightings(List<OfficerSighting> sightings) {
        OfficerSighting previouslySelected = selected;
        OfficerSighting match = null;
        applyingRows = true;
        try {
            officersModel.setRows(sightings);
            TableStyler.packColumns(officersTable);
            if (previouslySelected != null) {
                for (int row = 0; row < sightings.size(); row++) {
                    if (sameSighting(sightings.get(row), previouslySelected)) {
                        match = sightings.get(row);
                        int viewRow = officersTable.convertRowIndexToView(row);
                        officersTable.getSelectionModel().setSelectionInterval(viewRow, viewRow);
                        break;
                    }
                }
            }
        } finally {
            applyingRows = false;
        }
        updateSummary(sightings);
        if (match != null) {
            refreshDetail(match);
        } else if (previouslySelected != null) {
            showDetail(null);
        }
    }

    private static boolean sameSighting(OfficerSighting a, OfficerSighting b) {
        return a.characterId() == b.characterId() && a.officerName().equals(b.officerName())
                && a.firstSeenAt().equals(b.firstSeenAt());
    }

    private void updateSummary(List<OfficerSighting> sightings) {
        NpcCatalog catalog = appContext.officerService.catalog();
        if (catalog.isEmpty()) {
            summaryLabel.setText("The NPC catalog isn't downloaded yet, so officers can't be recognized. "
                    + "Scan Gamelogs downloads it (needs internet).");
            return;
        }
        if (sightings.isEmpty()) {
            summaryLabel.setText("No officers in your Gamelogs yet. After a hunt, click Scan Gamelogs "
                    + "(Update > NPC Kills does the same).");
            return;
        }
        long killed = sightings.stream().filter(OfficerSighting::killed).count();
        double total = sightings.stream().mapToDouble(OfficerSighting::totalValue).sum();
        OfficerSighting latest = sightings.get(0);
        summaryLabel.setText(sightings.size() + (sightings.size() == 1 ? " sighting" : " sightings")
                + " · " + killed + " killed · bounties + drops: " + IskFormatter.format(total)
                + " · latest: " + latest.officerName() + ", " + DateUtil.formatEveTime(latest.firstSeenAt()) + " EVE");
    }

    private void showDetail(OfficerSighting sighting) {
        selected = sighting;
        int generation = detailGeneration.incrementAndGet();
        boolean present = sighting != null;
        beltCombo.setEnabled(present);
        notesField.setEnabled(present);
        saveButton.setEnabled(present);
        addDropButton.setEnabled(present);
        removeDropButton.setEnabled(false);
        spawnModel.setRows(List.of());
        dropsModel.setRows(List.of());
        dropsTotalLabel.setText("");
        beltModel.removeAllElements();

        if (!present) {
            headerLabel.setText("Select an officer above to see its spawn.");
            setDetailText(timingText, "");
            setDetailText(payoutText, "");
            notesField.setText("");
            return;
        }

        notesField.setText(sighting.notes() == null ? "" : sighting.notes());
        beltCombo.getEditor().setItem(sighting.belt() == null ? "" : sighting.belt());
        refreshDetail(sighting);

        new SwingWorker<List<SpawnMember>, Void>() {
            @Override
            protected List<SpawnMember> doInBackground() {
                return appContext.officerService.listSpawn(sighting.encounterId());
            }

            @Override
            protected void done() {
                if (generation != detailGeneration.get()) {
                    return;
                }
                try {
                    spawnModel.setRows(get());
                    TableStyler.packColumns(spawnTable);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Failed to load the spawn for " + sighting.officerName(), e);
                }
            }
        }.execute();

        new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() {
                return appContext.officerService.listBelts(sighting.solarSystem());
            }

            @Override
            protected void done() {
                if (generation != detailGeneration.get()) {
                    return;
                }
                try {
                    String typed = Objects.toString(beltCombo.getEditor().getItem(), "");
                    for (String belt : get()) {
                        beltModel.addElement(belt);
                    }
                    beltCombo.getEditor().setItem(typed);
                } catch (Exception e) {
                    LOG.log(Level.INFO, "Couldn't load belts for " + sighting.solarSystem(), e);
                }
            }
        }.execute();
    }

    private void refreshDetail(OfficerSighting sighting) {
        selected = sighting;
        headerLabel.setText(sighting.officerName() + "  ·  " + OfficersTableModel.shortGroupName(sighting.officerGroup())
                + "  ·  " + sighting.characterName());
        setDetailText(timingText, timingDescription(sighting));
        setDetailText(payoutText, payoutDescription(sighting));
        loadDrops(sighting);
    }

    private static void setDetailText(JTextArea area, String text) {
        area.setText(text);
        area.setVisible(!text.isEmpty());
    }

    private void loadDrops(OfficerSighting sighting) {
        int generation = dropsGeneration.incrementAndGet();
        new SwingWorker<List<OfficerDrop>, Void>() {
            @Override
            protected List<OfficerDrop> doInBackground() {
                return appContext.officerService.listDrops(sighting);
            }

            @Override
            protected void done() {
                if (generation != dropsGeneration.get()) {
                    return;
                }
                try {
                    List<OfficerDrop> drops = get();
                    dropsModel.setRows(drops);
                    TableStyler.packColumns(dropsTable);
                    removeDropButton.setEnabled(false);
                    double dropValue = drops.stream().mapToDouble(OfficerDrop::totalValue).sum();
                    dropsTotalLabel.setText(drops.isEmpty() ? "" : IskFormatter.format(dropValue));
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Failed to load drops for " + sighting.officerName(), e);
                }
            }
        }.execute();
    }

    private static String timingDescription(OfficerSighting sighting) {
        String system = sighting.solarSystem() == null ? KillDayTypeRow.UNKNOWN_SYSTEM : sighting.solarSystem();
        String fight = "fight " + DateUtil.formatEveClock(sighting.fightStartedAt()) + "–"
                + DateUtil.formatEveClock(sighting.fightEndedAt()) + " EVE";
        String escort = sighting.escortKills() + " other NPC" + (sighting.escortKills() == 1 ? "" : "s")
                + " killed in the same fight";
        if (!sighting.killed()) {
            return "First seen " + DateUtil.formatEveTime(sighting.firstSeenAt()) + " EVE in " + system
                    + " - no bounty was recorded for it in this fight (" + fight + "). " + escort + ".";
        }
        return "First seen " + DateUtil.formatEveTime(sighting.firstSeenAt()) + " EVE, killed "
                + DateUtil.formatEveClock(sighting.killedAt()) + " ("
                + formatDuration(Duration.between(sighting.firstSeenAt(), sighting.killedAt()))
                + " later) in " + system + " · " + fight + " · " + escort + ".";
    }

    private String payoutDescription(OfficerSighting sighting) {
        if (sighting.payout() != null) {
            String system = sighting.payout().solarSystem();
            return "✓ Confirmed by the bounty payout at " + DateUtil.formatEveTime(sighting.payout().paidAt())
                    + " EVE" + (system == null ? "" : " in " + system) + ": "
                    + IskFormatter.format(sighting.payout().amount()) + " for "
                    + appContext.officerService.describePayoutNpcs(sighting.payout()) + ".";
        }
        if (sighting.killed()) {
            return "Not confirmed by the wallet journal yet. Run Update > Journal within 30 days of the "
                    + "kill - ESI only returns the last 30 days, and the payout is saved here once found.";
        }
        return "";
    }

    private static String formatDuration(Duration duration) {
        long seconds = Math.max(0, duration.getSeconds());
        return seconds >= 60 ? (seconds / 60) + "m " + (seconds % 60) + "s" : seconds + "s";
    }

    private void saveDetails() {
        OfficerSighting sighting = selected;
        if (sighting == null) {
            return;
        }
        String belt = Objects.toString(beltCombo.getEditor().getItem(), "");
        String notes = notesField.getText();
        saveButton.setEnabled(false);
        runThenReload(() -> appContext.officerService.saveDetails(sighting, belt, notes),
                "Failed to save officer details", () -> saveButton.setEnabled(selected != null));
    }

    private void addDrop() {
        OfficerSighting sighting = selected;
        if (sighting == null) {
            return;
        }
        AddDropDialog.Result drop = AddDropDialog.showDialog(SwingUtilities.getWindowAncestor(this), appContext,
                sighting.officerName());
        if (drop == null) {
            return;
        }
        runThenReload(() -> appContext.officerService.addDrop(sighting, drop.item(), drop.quantity(), drop.unitPrice()),
                "Failed to add a drop", () -> { });
    }

    private void removeDrop() {
        int viewRow = dropsTable.getSelectedRow();
        if (viewRow < 0) {
            return;
        }
        OfficerDrop drop = dropsModel.rowAt(dropsTable.convertRowIndexToModel(viewRow));
        removeDropButton.setEnabled(false);
        runThenReload(() -> appContext.officerService.removeDrop(drop), "Failed to remove a drop", () -> { });
    }

    private void runThenReload(Runnable write, String failureMessage, Runnable afterwards) {
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                write.run();
                return null;
            }

            @Override
            protected void done() {
                afterwards.run();
                try {
                    get();
                } catch (Exception e) {
                    LOG.log(Level.WARNING, failureMessage, e);
                }
                reload();
            }
        }.execute();
    }

    private void scanGamelogs() {
        scanButton.setEnabled(false);
        scanButton.setText("Scanning...");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                appContext.killService.refreshKillsFromLogs();
                return null;
            }

            @Override
            protected void done() {
                scanButton.setEnabled(true);
                scanButton.setText("Scan Gamelogs");
                try {
                    get();
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Gamelog scan failed", e);
                }
                reload();
            }
        }.execute();
    }
}
