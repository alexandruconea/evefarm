package com.evefarm.ui;

import com.evefarm.AppContext;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.model.MarketOrderRow;
import com.evefarm.ui.column.ColumnCopySupport;
import com.evefarm.ui.column.ColumnSorting;
import com.evefarm.ui.column.ColumnVisibilitySupport;
import com.evefarm.ui.filter.FilterBarPanel;
import com.evefarm.util.IskFormatter;

import javax.swing.GroupLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.LayoutStyle;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class MarketOrdersPanel extends JPanel {

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
        ColumnSorting.install(sorter, tableModel);
        table.setRowSorter(sorter);
        TableStyler.style(table);
        ColumnVisibilitySupport.install(table, tableModel, PANEL_KEY, appContext.tableColumnStateDao);
        ColumnCopySupport.install(table, tableModel);

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

    private JPanel filterBarContainer;
    private JScrollPane jScrollPane2;
    private JLabel orderCountLabel;
    private JCheckBox showClosedCheckBox;
    private JPanel bottomBar;
    private JTable table;

    private void initComponents() {

        filterBarContainer = new JPanel();
        jScrollPane2 = new JScrollPane();
        table = new JTable();
        orderCountLabel = new JLabel();
        showClosedCheckBox = new JCheckBox("Show closed orders");
        bottomBar = new JPanel(new BorderLayout(12, 0));
        bottomBar.add(orderCountLabel, BorderLayout.CENTER);
        bottomBar.add(showClosedCheckBox, BorderLayout.EAST);

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

        orderCountLabel.setText("0 orders");

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
