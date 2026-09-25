package com.evefarm.ui;

import com.evefarm.AppContext;
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

        loadRowsFromDatabase();
    }

    public void onShown() {
        loadRowsFromDatabase();
    }

    public void refreshCharacterFilter() {
        loadRowsFromDatabase();
    }

    private void loadRowsFromDatabase() {
        BackgroundLoader.load(loadGeneration, () -> appContext.marketOrderService.getOrderRows(null),
                this::applyRows, "market orders",
                () -> orderCountLabel.setText("Couldn't load market orders - see the log"));
    }

    private void applyRows(List<MarketOrderRow> rows) {
        tableModel.setRows(rows);
        double totalBrokerFees = rows.stream()
                .filter(r -> r.brokerFee() != null)
                .mapToDouble(MarketOrderRow::brokerFee)
                .sum();
        orderCountLabel.setText(rows.size() + " orders · Total broker's fees: "
                + IskFormatter.format(totalBrokerFees));
        TableStyler.packColumns(table);
        applyFilter();
    }

    private void applyFilter() {
        sorter.setRowFilter(filterBarPanel.buildRowFilter());
        filterBarPanel.setRowCounts(table.getRowCount(), tableModel.getRowCount());
    }

    private javax.swing.JPanel filterBarContainer;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JLabel orderCountLabel;
    private javax.swing.JTable table;
    private void initComponents() {

        filterBarContainer = new javax.swing.JPanel();
        jScrollPane2 = new javax.swing.JScrollPane();
        table = new javax.swing.JTable();
        orderCountLabel = new javax.swing.JLabel();

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
                    .addComponent(orderCountLabel))
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
                .addComponent(orderCountLabel)
                .addContainerGap())
        );
    }
}
