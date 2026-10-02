package com.evefarm.ui;

import com.evefarm.AppContext;
import com.evefarm.db.dao.AssetDao;
import com.evefarm.model.AssetRow;
import com.evefarm.ui.column.ColumnCopySupport;
import com.evefarm.ui.column.ColumnSorting;
import com.evefarm.ui.column.ColumnVisibilitySupport;
import com.evefarm.ui.filter.FilterBarPanel;
import com.evefarm.util.DateUtil;
import com.evefarm.util.IskFormatter;

import javax.swing.GroupLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.LayoutStyle;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public final class AssetsPanel extends JPanel {

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
    private FilterBarPanel filterBarPanel;
    private TableRowSorter<AssetsTableModel> sorter;

    public AssetsPanel(AppContext appContext) {
        initComponents();
        this.appContext = appContext;
        postInit();
    }

    private void postInit() {
        table.setModel(tableModel);
        sorter = new TableRowSorter<>(tableModel);
        ColumnSorting.install(sorter, tableModel);
        table.setRowSorter(sorter);
        TableStyler.style(table);
        ColumnVisibilitySupport.install(table, tableModel, PANEL_KEY, appContext.tableColumnStateDao);
        ColumnCopySupport.install(table, tableModel);

        filterBarPanel = new FilterBarPanel(PANEL_KEY, tableModel.columnNames(), appContext.savedFilterDao);
        filterBarPanel.setOnFilterChanged(this::applyFilter);
        filterBarContainer.setLayout(new BorderLayout());
        filterBarContainer.add(filterBarPanel, BorderLayout.CENTER);

        snapshotCombo.addItem(CURRENT);
        snapshotCombo.addActionListener(e -> {
            if (!updatingSnapshots) {
                loadRowsFromDatabase();
            }
        });

        loadRowsFromDatabase();
    }

    public void onShown() {
        loadRowsFromDatabase();
    }

    public void refreshCharacterFilter() {
        loadRowsFromDatabase();
    }

    private void loadRowsFromDatabase() {
        Snapshot snapshot = (Snapshot) snapshotCombo.getSelectedItem();
        String month = snapshot == null ? null : snapshot.month();
        BackgroundLoader.load(loadGeneration, () -> new Loaded(appContext.assetService.getArchivedMonths(),
                month == null ? appContext.assetService.getAssetRows(null)
                        : appContext.assetService.getArchivedAssetRows(month, null)), loaded -> {
            showSnapshots(loaded.months(), month);
            tableModel.setRows(loaded.rows());
            totalLabel.setText((month == null ? "Total: " : "Total in " + monthName(month) + ": ")
                    + IskFormatter.format(tableModel.totalValue()));
            TableStyler.packColumns(table);
            applyFilter();
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

    private void applyFilter() {
        sorter.setRowFilter(filterBarPanel.buildRowFilter());
        filterBarPanel.setRowCounts(table.getRowCount(), tableModel.getRowCount());
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
        JPanel snapshotPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        snapshotPanel.add(new JLabel("Show:"));
        snapshotPanel.add(snapshotCombo);
        bottomBar = new JPanel(new BorderLayout(12, 0));
        bottomBar.add(totalLabel, BorderLayout.CENTER);
        bottomBar.add(snapshotPanel, BorderLayout.EAST);

        GroupLayout filterBarContainerLayout = new GroupLayout(filterBarContainer);
        filterBarContainer.setLayout(filterBarContainerLayout);
        filterBarContainerLayout.setHorizontalGroup(
            filterBarContainerLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGap(0, 880, Short.MAX_VALUE)
        );
        filterBarContainerLayout.setVerticalGroup(
            filterBarContainerLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGap(0, 60, Short.MAX_VALUE)
        );

        jScrollPane2.setViewportView(table);

        totalLabel.setText("Total: 0.00 ISK");

        GroupLayout layout = new GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                    .addComponent(filterBarContainer, GroupLayout.DEFAULT_SIZE, 880, Short.MAX_VALUE)
                    .addComponent(jScrollPane2, GroupLayout.DEFAULT_SIZE, 880, Short.MAX_VALUE)
                    .addComponent(bottomBar, GroupLayout.DEFAULT_SIZE, 880, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(filterBarContainer, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane2, GroupLayout.DEFAULT_SIZE, 380, Short.MAX_VALUE)
                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(bottomBar, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
    }
}
