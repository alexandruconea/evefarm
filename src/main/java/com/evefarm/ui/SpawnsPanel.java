package com.evefarm.ui;

import com.evefarm.AppContext;
import com.evefarm.model.CharacterContribution;
import com.evefarm.model.SpawnMember;
import com.evefarm.model.SpawnRow;
import com.evefarm.util.DateUtil;
import com.evefarm.util.IskFormatter;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SpawnsPanel extends JPanel {

    private static final Logger LOG = Logger.getLogger(SpawnsPanel.class.getName());
    private static final String PANEL_KEY = "spawns";

    private final AppContext appContext;
    private final SpawnsTableModel tableModel = new SpawnsTableModel();
    private final JTable table = new JTable();
    private final JPanel filterBarContainer = new JPanel();
    private final JLabel countLabel = new JLabel("0 spawns");
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
        listPanel.add(countLabel, BorderLayout.SOUTH);

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
        detailHeader.setText(DateUtil.formatEveMinute(spawn.startedAt()) + " EVE  ·  "
                + (spawn.solarSystem() == null ? "Unknown system" : spawn.solarSystem()) + "  ·  "
                + spawn.kind() + "  ·  " + spawn.killed() + " killed"
                + (spawn.bounty() > 0 ? "  ·  " + IskFormatter.format(spawn.bounty()) : "")
                + "  ·  " + SpawnsTableModel.duration(spawn.durationSeconds()));
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
