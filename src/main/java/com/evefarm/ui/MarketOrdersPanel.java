package com.evefarm.ui;

import com.evefarm.AppContext;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.model.MarketOrderRow;
import com.evefarm.ui.column.ColumnVisibilitySupport;
import com.evefarm.ui.filter.FilterBarPanel;
import com.evefarm.util.IskFormatter;

import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class MarketOrdersPanel extends javax.swing.JPanel {

    private static final String PANEL_KEY = "marketOrders";

    private final AppContext appContext;
    private final MarketOrdersTableModel tableModel = new MarketOrdersTableModel();
    private final AtomicInteger loadGeneration = new AtomicInteger();
    private FilterBarPanel filterBarPanel;
    private TableRowSorter<MarketOrdersTableModel> sorter;

    public MarketOrdersPanel(AppContext appContext) {
        initComponents();
        this.appContext = appContext;
        postInit();
    }

    private void postInit() {
        table.setModel(tableModel);
        sorter = new TableRowSorter<>(tableModel);
        com.evefarm.ui.column.ColumnSorting.installNumericAwareComparators(sorter, tableModel);
        table.setRowSorter(sorter);
        TableStyler.style(table);
        ColumnVisibilitySupport.install(table, tableModel, PANEL_KEY, appContext.tableColumnStateDao);
        com.evefarm.ui.column.ColumnCopySupport.install(table, tableModel);

        filterBarPanel = new FilterBarPanel(PANEL_KEY, tableModel.columnNames(), appContext.savedFilterDao);
        filterBarPanel.setOnFilterChanged(this::applyFilter);
        filterBarContainer.setLayout(new BorderLayout());
        filterBarContainer.add(filterBarPanel, BorderLayout.CENTER);

        showClosedCheckBox.setSelected(Boolean.parseBoolean(
                appContext.settingsDao.getOrDefault(SettingsDao.MARKET_ORDERS_SHOW_CLOSED, "false")));
        showClosedCheckBox.addActionListener(e -> {
            appContext.settingsDao.set(SettingsDao.MARKET_ORDERS_SHOW_CLOSED,
                    String.valueOf(showClosedCheckBox.isSelected()));
            loadRowsFromDatabase();
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
        boolean includeClosed = showClosedCheckBox.isSelected();
        BackgroundLoader.load(loadGeneration, () -> appContext.marketOrderService.getOrderRows(null, includeClosed),
                this::applyRows, "market orders",
                () -> orderCountLabel.setText("Couldn't load market orders - see the log"));
    }

    private void applyRows(List<MarketOrderRow> rows) {
        tableModel.setRows(rows);
        orderCountLabel.setText(summary(rows));
        TableStyler.packColumns(table);
        applyFilter();
    }

    static String summary(List<MarketOrderRow> rows) {
        List<MarketOrderRow> active = rows.stream().filter(MarketOrderRow::active).toList();
        double totalBrokerFees = active.stream()
                .filter(r -> r.brokerFee() != null)
                .mapToDouble(MarketOrderRow::brokerFee)
                .sum();
        int closed = rows.size() - active.size();
        String summary = count(active.size(), closed == 0 ? "order" : "active order")
                + " · Total value: " + totalValue(active)
                + " · Total broker's fees: " + IskFormatter.format(totalBrokerFees);
        return closed == 0 ? summary : summary + " · " + count(closed, "closed order");
    }

    private static String count(int number, String noun) {
        return number + " " + noun + (number == 1 ? "" : "s");
    }

    private static String totalValue(List<MarketOrderRow> rows) {
        boolean anySell = rows.stream().anyMatch(r -> !r.isBuyOrder());
        boolean anyBuy = rows.stream().anyMatch(MarketOrderRow::isBuyOrder);
        double sell = valueOf(rows, false);
        double buy = valueOf(rows, true);
        if (anySell && anyBuy) {
            return IskFormatter.format(sell) + " sell, " + IskFormatter.format(buy) + " buy";
        }
        return IskFormatter.format(sell + buy);
    }

    private static double valueOf(List<MarketOrderRow> rows, boolean buyOrders) {
        return rows.stream()
                .filter(r -> r.isBuyOrder() == buyOrders)
                .mapToDouble(r -> r.price() * r.volumeRemain())
                .sum();
    }

    private void applyFilter() {
        sorter.setRowFilter(filterBarPanel.buildRowFilter());
        filterBarPanel.setRowCounts(table.getRowCount(), tableModel.getRowCount());
    }

    private javax.swing.JPanel filterBarContainer;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JLabel orderCountLabel;
    private javax.swing.JCheckBox showClosedCheckBox;
    private javax.swing.JPanel bottomBar;
    private javax.swing.JTable table;
    private void initComponents() {

        filterBarContainer = new javax.swing.JPanel();
        jScrollPane2 = new javax.swing.JScrollPane();
        table = new javax.swing.JTable();
        orderCountLabel = new javax.swing.JLabel();
        showClosedCheckBox = new javax.swing.JCheckBox("Show closed orders");
        bottomBar = new javax.swing.JPanel(new BorderLayout(12, 0));
        bottomBar.add(orderCountLabel, BorderLayout.CENTER);
        bottomBar.add(showClosedCheckBox, BorderLayout.EAST);

        javax.swing.GroupLayout filterBarContainerLayout = new javax.swing.GroupLayout(filterBarContainer);
        filterBarContainer.setLayout(filterBarContainerLayout);
        filterBarContainerLayout.setHorizontalGroup(
            filterBarContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 880, Short.MAX_VALUE)
        );
        filterBarContainerLayout.setVerticalGroup(
            filterBarContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 60, Short.MAX_VALUE)
        );

        jScrollPane2.setViewportView(table);

        orderCountLabel.setText("0 orders");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(filterBarContainer, javax.swing.GroupLayout.DEFAULT_SIZE, 880, Short.MAX_VALUE)
                    .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 880, Short.MAX_VALUE)
                    .addComponent(bottomBar, javax.swing.GroupLayout.DEFAULT_SIZE, 880, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(filterBarContainer, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 380, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(bottomBar, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
    }
}
