package com.evefarm.ui;

import com.evefarm.AppContext;
import com.evefarm.db.dao.AssetDao;
import com.evefarm.model.AssetRow;
import com.evefarm.util.DateUtil;
import com.evefarm.util.IskFormatter;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public final class AssetsPanel extends TabPanel {

    private static final String PANEL_KEY = "assets";

    private record Snapshot(String month, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private record Loaded(List<AssetDao.ArchivedMonth> months, List<AssetRow> rows) {
    }

    private static final Snapshot CURRENT = new Snapshot(null, "Current assets");
    private boolean updatingSnapshots;

    private final AppContext appContext;
    private final AssetsTableModel tableModel = new AssetsTableModel();
    private final AtomicInteger loadGeneration = new AtomicInteger();
    private FilteredTable<AssetRow> filteredTable;

    public AssetsPanel(AppContext appContext) {
        initComponents();
        this.appContext = appContext;
        postInit();
    }

    private void postInit() {
        filteredTable = new FilteredTable<>(appContext, PANEL_KEY, tableModel, table, filterBarContainer);

        snapshotCombo.addItem(CURRENT);
        snapshotCombo.addActionListener(e -> {
            if (!updatingSnapshots) {
                loadRowsFromDatabase();
            }
        });

        loadRowsFromDatabase();
    }

    @Override
    public void onShown() {
        loadRowsFromDatabase();
    }

    @Override
    public void refreshCharacterFilter() {
        loadRowsFromDatabase();
    }

    private void loadRowsFromDatabase() {
        Snapshot snapshot = (Snapshot) snapshotCombo.getSelectedItem();
        String month = snapshot == null ? null : snapshot.month();
        BackgroundLoader.load(loadGeneration, () -> new Loaded(appContext.assetService.getArchivedMonths(),
                month == null ? appContext.assetService.getAssetRows()
                        : appContext.assetService.getArchivedAssetRows(month)), loaded -> {
            showSnapshots(loaded.months(), month);
            filteredTable.showRows(loaded.rows());
            totalLabel.setText((month == null ? "Total: " : "Total in " + monthName(month) + ": ")
                    + IskFormatter.format(tableModel.totalValue()));
        }, "assets", () -> totalLabel.setText("Couldn't load assets - see the log"));
    }

    private void showSnapshots(List<AssetDao.ArchivedMonth> months, String selectedMonth) {
        updatingSnapshots = true;
        try {
            snapshotCombo.removeAllItems();
            snapshotCombo.addItem(CURRENT);
            Snapshot selected = CURRENT;
            for (AssetDao.ArchivedMonth month : months) {
                Snapshot snapshot = new Snapshot(month.month(), monthName(month.month()) + " (saved "
                        + DateUtil.formatIsoInstant(month.lastSavedAt()) + ")");
                snapshotCombo.addItem(snapshot);
                if (month.month().equals(selectedMonth)) {
                    selected = snapshot;
                }
            }
            snapshotCombo.setSelectedItem(selected);
        } finally {
            updatingSnapshots = false;
        }
    }

    static String monthName(String month) {
        try {
            return YearMonth.parse(month).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US));
        } catch (DateTimeParseException e) {
            return month;
        }
    }

    private JPanel filterBarContainer;
    private JScrollPane jScrollPane2;
    private JTable table;
    private JLabel totalLabel;
    private JComboBox<Snapshot> snapshotCombo;
    private JPanel bottomBar;

    private void initComponents() {

        filterBarContainer = new JPanel();
        jScrollPane2 = new JScrollPane();
        table = new JTable();
        totalLabel = new JLabel();
        snapshotCombo = new JComboBox<>();
        snapshotCombo.setPrototypeDisplayValue(new Snapshot("2026-09", "September 2026 (saved 2026-09-30 23:59)"));
        ComboSizing.options(snapshotCombo);
        JPanel snapshotPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        snapshotPanel.add(new JLabel("Show:"));
        snapshotPanel.add(snapshotCombo);
        bottomBar = new JPanel(new BorderLayout(12, 0));
        bottomBar.add(totalLabel, BorderLayout.CENTER);
        bottomBar.add(snapshotPanel, BorderLayout.EAST);

        jScrollPane2.setViewportView(table);

        totalLabel.setText("Total: 0.00 ISK");

        FilteredTable.layOut(this, filterBarContainer, jScrollPane2, bottomBar);
    }
}
