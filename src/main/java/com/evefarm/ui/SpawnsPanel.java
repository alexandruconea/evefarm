package com.evefarm.ui;

import com.evefarm.AppContext;
import com.evefarm.model.CharacterContribution;
import com.evefarm.model.SpawnMember;
import com.evefarm.model.SpawnRow;
import com.evefarm.util.DateUtil;
import com.evefarm.util.IskFormatter;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SpawnsPanel extends JPanel {

    private static final Logger LOG = Logger.getLogger(SpawnsPanel.class.getName());
    private static final String PANEL_KEY = "spawns";
    private static final String SAVE_TITLE = "Save Spawns Report";

    private final AppContext appContext;
    private final SpawnsTableModel tableModel = new SpawnsTableModel();
    private final JTable table = new JTable();
    private final JPanel filterBarContainer = new JPanel();
    private final JLabel countLabel = new JLabel("0 spawns");
    private final JButton saveButton = new JButton("Save as .txt...", Icons.DOCUMENT);
    private final SpawnTableModel detailModel = new SpawnTableModel();
    private final JTable detailTable = new JTable(detailModel);
    private final JLabel detailHeader = new JLabel("Select a spawn to see its NPCs.");
    private final JLabel fightersLabel = new JLabel(" ");
    private final AtomicInteger detailGeneration = new AtomicInteger();
    private final DataTablePanelSupport<SpawnRow> support;

    public SpawnsPanel(AppContext appContext) {
        this.appContext = appContext;
        buildUi();
        support = new DataTablePanelSupport<>(appContext, PANEL_KEY, tableModel, table, filterBarContainer,
                countLabel, "spawns", appContext.officerService::listAllSpawns);
        support.setOnRowsShown(() -> saveButton.setEnabled(table.getRowCount() > 0));
        support.init();
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showDetail(selectedRow());
            }
        });
    }

    public void onShown() {
        support.reload();
    }

    private void buildUi() {
        setLayout(new BorderLayout());

        JPanel listPanel = new JPanel(new BorderLayout());
        listPanel.add(filterBarContainer, BorderLayout.NORTH);
        listPanel.add(new JScrollPane(table), BorderLayout.CENTER);
        saveButton.setEnabled(false);
        saveButton.setToolTipText("Save the spawns shown in the list, each with its NPCs, to a text file");
        saveButton.addActionListener(e -> saveReport());
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        footer.add(countLabel, BorderLayout.WEST);
        footer.add(saveButton, BorderLayout.EAST);
        listPanel.add(footer, BorderLayout.SOUTH);

        detailHeader.setFont(detailHeader.getFont().deriveFont(Font.BOLD));
        detailHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        fightersLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        fightersLabel.putClientProperty("html.disable", Boolean.TRUE);
        fightersLabel.setVisible(false);
        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(8, 0, 4, 0));
        headerPanel.add(detailHeader);
        headerPanel.add(fightersLabel);
        TableStyler.style(detailTable);
        JPanel detailPanel = new JPanel(new BorderLayout());
        detailPanel.add(headerPanel, BorderLayout.NORTH);
        detailPanel.add(new JScrollPane(detailTable), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, listPanel, detailPanel);
        split.setResizeWeight(0.65);
        split.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
        add(split, BorderLayout.CENTER);
    }

    static String headline(SpawnRow spawn) {
        return DateUtil.formatEveMinute(spawn.startedAt()) + " EVE  ·  "
                + (spawn.solarSystem() == null ? "Unknown system" : spawn.solarSystem()) + "  ·  "
                + spawn.kind() + "  ·  " + spawn.killed() + " killed"
                + (spawn.bounty() > 0 ? "  ·  " + IskFormatter.format(spawn.bounty()) : "")
                + "  ·  " + SpawnsTableModel.duration(spawn.durationSeconds());
    }

    static String fightersDescription(List<CharacterContribution> contributions) {
        StringBuilder text = new StringBuilder("Fought by ");
        for (int i = 0; i < contributions.size(); i++) {
            CharacterContribution contribution = contributions.get(i);
            if (i > 0) {
                text.append("  ·  ");
            }
            text.append(contribution.characterName()).append(": ").append(contribution.kills())
                    .append(contribution.kills() == 1 ? " kill" : " kills");
            if (contribution.bounty() > 0) {
                text.append(", ").append(IskFormatter.format(contribution.bounty()));
            }
            text.append(", ").append(String.format(Locale.US, "%,d", contribution.damageDealt())).append(" damage");
        }
        return text.toString();
    }

    private void saveReport() {
        List<SpawnRow> spawns = support.visibleRows();
        if (spawns.isEmpty()) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(SAVE_TITLE);
        chooser.setSelectedFile(new File("spawns-" + LocalDate.now() + ".txt"));
        chooser.setFileFilter(new FileNameExtensionFilter("Text file (*.txt)", "txt"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File chosen = chooser.getSelectedFile();
        Path target = chosen.getName().toLowerCase(Locale.ROOT).endsWith(".txt") ? chosen.toPath()
                : chosen.toPath().resolveSibling(chosen.getName() + ".txt");
        if (Files.exists(target) && JOptionPane.showConfirmDialog(this, target.getFileName()
                + " already exists. Replace it?", SAVE_TITLE, JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        saveButton.setEnabled(false);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                Files.write(target, SpawnReport.lines(spawns,
                        spawn -> appContext.officerService.listSpawn(spawn.encounterIds()), Instant.now()),
                        StandardCharsets.UTF_8);
                return null;
            }

            @Override
            protected void done() {
                saveButton.setEnabled(table.getRowCount() > 0);
                try {
                    get();
                    JOptionPane.showMessageDialog(SpawnsPanel.this, "Report saved to:\n" + target, SAVE_TITLE,
                            JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Failed to save the spawns report", e);
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    JOptionPane.showMessageDialog(SpawnsPanel.this, "Couldn't save the report: "
                            + cause.getMessage(), SAVE_TITLE, JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private SpawnRow selectedRow() {
        int viewRow = table.getSelectedRow();
        return viewRow < 0 ? null : tableModel.rowAt(table.convertRowIndexToModel(viewRow));
    }

    private void showDetail(SpawnRow spawn) {
        int generation = detailGeneration.incrementAndGet();
        if (spawn == null) {
            detailHeader.setText("Select a spawn to see its NPCs.");
            fightersLabel.setVisible(false);
            detailModel.setRows(List.of());
            return;
        }
        fightersLabel.setText(fightersDescription(spawn.contributions()));
        fightersLabel.setVisible(spawn.contributions().size() > 1);
        detailHeader.setText(headline(spawn));
        new SwingWorker<List<SpawnMember>, Void>() {
            @Override
            protected List<SpawnMember> doInBackground() {
                return appContext.officerService.listSpawn(spawn.encounterIds());
            }

            @Override
            protected void done() {
                if (generation != detailGeneration.get()) {
                    return;
                }
                try {
                    detailModel.setRows(get());
                    TableStyler.packColumns(detailTable);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Failed to load the NPCs of a spawn", e);
                }
            }
        }.execute();
    }
}
