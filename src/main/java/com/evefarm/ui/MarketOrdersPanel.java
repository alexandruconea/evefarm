package com.evefarm.ui;

import com.evefarm.AppContext;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.model.MarketOrderRow;
import com.evefarm.util.IskFormatter;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import java.awt.BorderLayout;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class MarketOrdersPanel extends TabPanel {

    private static final String PANEL_KEY = "marketOrders";

    private final AppContext appContext;
    private final MarketOrdersTableModel tableModel = new MarketOrdersTableModel();
    private final AtomicInteger loadGeneration = new AtomicInteger();
    private FilteredTable<MarketOrderRow> filteredTable;

    public MarketOrdersPanel(AppContext appContext) {
        initComponents();
        this.appContext = appContext;
        postInit();
    }

    private void postInit() {
        filteredTable = new FilteredTable<>(appContext, PANEL_KEY, tableModel, table, filterBarContainer);

        showClosedCheckBox.setSelected(Boolean.parseBoolean(
                appContext.settingsDao.getOrDefault(SettingsDao.MARKET_ORDERS_SHOW_CLOSED, "false")));
        showClosedCheckBox.addActionListener(e -> {
            appContext.settingsDao.set(SettingsDao.MARKET_ORDERS_SHOW_CLOSED,
                    String.valueOf(showClosedCheckBox.isSelected()));
            loadRowsFromDatabase();
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
        boolean includeClosed = showClosedCheckBox.isSelected();
        BackgroundLoader.load(loadGeneration, () -> appContext.marketOrderService.getOrderRows(includeClosed),
                this::applyRows, "market orders",
                () -> orderCountLabel.setText("Couldn't load market orders - see the log"));
    }

    private void applyRows(List<MarketOrderRow> rows) {
        orderCountLabel.setText(summary(rows));
        filteredTable.showRows(rows);
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

        jScrollPane2.setViewportView(table);

        orderCountLabel.setText("0 orders");

        FilteredTable.layOut(this, filterBarContainer, jScrollPane2, bottomBar);
    }
}
