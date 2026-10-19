package com.evefarm.ui;

import com.evefarm.AppContext;
import com.evefarm.service.LpPurchaseService;
import com.evefarm.service.LpPurchaseService.Holding;
import com.evefarm.service.LpPurchaseService.Lot;
import com.evefarm.ui.column.ColumnSorting;
import com.evefarm.ui.column.ColumnVisibilitySupport;
import com.evefarm.util.IskFormatter;
import com.formdev.flatlaf.FlatLaf;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class LpPurchasesPanel extends TabPanel {

    private static final Logger LOG = Logger.getLogger(LpPurchasesPanel.class.getName());

    private record Loaded(List<Lot> lots, int pendingPayments) {
    }

    private final AppContext appContext;
    private final LpHoldingsTableModel holdingsModel = new LpHoldingsTableModel();
    private final JTable holdingsTable = new JTable(holdingsModel);
    private final LpLotsTableModel lotsModel = new LpLotsTableModel();
    private final JTable lotsTable = new JTable(lotsModel);
    private final JButton reviewButton = new JButton("Review Payments...", Icons.REFRESH);
    private final JButton editButton = new JButton("Edit...", Icons.GEAR);
    private final JButton deleteButton = new JButton("Delete", Icons.REMOVE);
    private final JCheckBox showSoldBox = new JCheckBox("Show sold");
    private final JLabel summaryLabel = new JLabel(" ");
    private final AtomicInteger generation = new AtomicInteger();
    private List<Lot> lots = List.of();

    public LpPurchasesPanel(AppContext appContext) {
        this.appContext = appContext;
        buildUi();
    }

    @Override
    public void onShown() {
        reload();
    }

    @Override
    void onDataUpdated(boolean selected) {
        if (selected) {
            onShown();
        }
    }

    private void buildUi() {
        setLayout(new BorderLayout());
        reviewButton.setToolTipText("Your LP Store payments from the wallet journal that are not recorded yet. "
                + "EVE Farm suggests the offer each one bought");
        reviewButton.addActionListener(e -> new LpPaymentsDialog(SwingUtilities.getWindowAncestor(this),
                appContext, this::reload).setVisible(true));
        editButton.setToolTipText("Change the selected purchase: times bought, date, costs, target, units used "
                + "and notes");
        editButton.addActionListener(e -> edit());
        deleteButton.setToolTipText("Remove the selected purchase");
        deleteButton.addActionListener(e -> delete());
        showSoldBox.setToolTipText("Also list the items and purchases you have sold or used up");
        showSoldBox.addActionListener(e -> show(lots));
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        toolbar.add(ButtonSizing.row(6, reviewButton, editButton, deleteButton));
        toolbar.add(showSoldBox);
        add(toolbar, BorderLayout.NORTH);

        holdingsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        TableRowSorter<LpHoldingsTableModel> holdingsSorter = new TableRowSorter<>(holdingsModel);
        ColumnSorting.install(holdingsSorter, holdingsModel);
        holdingsTable.setRowSorter(holdingsSorter);
        holdingsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showLots();
            }
        });
        TableStyler.style(holdingsTable);
        ColumnVisibilitySupport.install(holdingsTable, holdingsModel, "lpHoldings", appContext.tableColumnStateDao);
        installSellNowColors();

        lotsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        TableRowSorter<LpLotsTableModel> lotsSorter = new TableRowSorter<>(lotsModel);
        ColumnSorting.install(lotsSorter, lotsModel);
        lotsTable.setRowSorter(lotsSorter);
        lotsTable.getSelectionModel().addListSelectionListener(e -> updateButtons());
        TableStyler.style(lotsTable);
        ColumnVisibilitySupport.install(lotsTable, lotsModel, "lpLots", appContext.tableColumnStateDao);

        JPanel lotsPanel = new JPanel(new BorderLayout(0, 4));
        JLabel lotsTitle = new JLabel("Purchases");
        lotsTitle.setToolTipText("Each purchase of the item selected above, or of every item when none is "
                + "selected. Sales are matched to the oldest purchase first");
        lotsPanel.add(lotsTitle, BorderLayout.NORTH);
        lotsPanel.add(new JScrollPane(lotsTable), BorderLayout.CENTER);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(holdingsTable), lotsPanel);
        split.setResizeWeight(0.5);
        split.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        add(split, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        footer.add(summaryLabel);
        add(footer, BorderLayout.SOUTH);
        updateButtons();
    }

    private void installSellNowColors() {
        TableCellRenderer base = holdingsTable.getDefaultRenderer(Object.class);
        holdingsTable.setDefaultRenderer(Object.class, (table, value, isSelected, hasFocus, row, column) -> {
            Component c = base.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            String key = holdingsModel.columns().get(table.convertColumnIndexToModel(column)).key();
            if (!isSelected && LpHoldingsTableModel.SELL_NOW.equals(key)) {
                Color color = colorFor(holdingsModel.rowAt(table.convertRowIndexToModel(row)));
                if (color != null) {
                    c.setBackground(color);
                }
            }
            return c;
        });
    }

    static Color colorFor(Holding holding) {
        if (holding.sellNow() == null || holding.remaining() <= 0) {
            return null;
        }
        boolean dark = FlatLaf.isLafDark();
        if (holding.targetPrice() != null && holding.sellNow() >= holding.targetPrice()) {
            return dark ? new Color(27, 94, 32) : new Color(200, 230, 201);
        }
        if (holding.sellNow() >= holding.breakEven()) {
            return dark ? new Color(110, 85, 10) : new Color(255, 236, 179);
        }
        return dark ? new Color(127, 29, 29) : new Color(255, 205, 210);
    }

    private void reload() {
        int current = generation.incrementAndGet();
        summaryLabel.setText("Loading...");
        new SwingWorker<Loaded, Void>() {
            @Override
            protected Loaded doInBackground() {
                int pendingPayments = appContext.lpPurchaseService.pendingPaymentCount();
                return new Loaded(appContext.lpPurchaseService.lots(), pendingPayments);
            }

            @Override
            protected void done() {
                if (current != generation.get()) {
                    return;
                }
                try {
                    Loaded loaded = get();
                    lots = loaded.lots();
                    reviewButton.setText(reviewText(loaded.pendingPayments()));
                    show(lots);
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    LOG.log(Level.WARNING, "Couldn't load the LP Store purchases", cause);
                    summaryLabel.setText("Couldn't load the purchases: " + cause.getMessage());
                }
            }
        }.execute();
    }

    static String reviewText(int pendingPayments) {
        return switch (pendingPayments) {
            case 0 -> "Review Payments...";
            case 1 -> "Review 1 Payment...";
            default -> "Review " + pendingPayments + " Payments...";
        };
    }

    private void show(List<Lot> all) {
        Integer selectedType = selectedHolding() == null ? null : selectedHolding().typeId();
        List<Holding> holdings = LpPurchaseService.holdings(all).stream()
                .filter(holding -> showSoldBox.isSelected() || holding.remaining() > 0)
                .toList();
        holdingsModel.setRows(holdings);
        TableStyler.packColumns(holdingsTable);
        for (int i = 0; selectedType != null && i < holdingsTable.getRowCount(); i++) {
            if (holdingsModel.rowAt(holdingsTable.convertRowIndexToModel(i)).typeId() == selectedType) {
                holdingsTable.setRowSelectionInterval(i, i);
            }
        }
        showLots();
        summaryLabel.setText(summary(holdings));
    }

    private Holding selectedHolding() {
        int viewRow = holdingsTable.getSelectedRow();
        return viewRow < 0 ? null : holdingsModel.rowAt(holdingsTable.convertRowIndexToModel(viewRow));
    }

    private Lot selectedLot() {
        int viewRow = lotsTable.getSelectedRow();
        return viewRow < 0 ? null : lotsModel.rowAt(lotsTable.convertRowIndexToModel(viewRow));
    }

    private void showLots() {
        Holding holding = selectedHolding();
        List<Lot> shown = (holding == null ? lots : holding.lots()).stream()
                .filter(lot -> showSoldBox.isSelected() || lot.remaining() > 0)
                .toList();
        lotsModel.setRows(shown);
        TableStyler.packColumns(lotsTable);
        updateButtons();
    }

    private void updateButtons() {
        boolean selected = selectedLot() != null;
        editButton.setEnabled(selected);
        deleteButton.setEnabled(selected);
    }

    static String summary(List<Holding> holdings) {
        if (holdings.isEmpty()) {
            return "No purchases yet. In the LP Store tab, select an offer and click Record Purchase..., or click "
                    + "Review Payments... to record the ones in your wallet journal.";
        }
        long units = holdings.stream().mapToLong(Holding::remaining).sum();
        double ifSold = holdings.stream().filter(h -> h.profitIfSoldNow() != null)
                .mapToDouble(Holding::profitIfSoldNow).sum();
        double realized = holdings.stream().mapToDouble(Holding::realizedProfit).sum();
        return String.format(Locale.US, "%d %s, %,d units left  ·  If sold now: %s  ·  Realized: %s",
                holdings.size(), holdings.size() == 1 ? "item" : "items", units, IskFormatter.format(ifSold),
                IskFormatter.format(realized));
    }

    private void edit() {
        Lot lot = selectedLot();
        if (lot == null) {
            return;
        }
        new LpPurchaseDialog(SwingUtilities.getWindowAncestor(this), "Edit Purchase", lot.purchase(), purchase -> {
            appContext.lpPurchaseService.update(purchase);
            reload();
        }).setVisible(true);
    }

    private void delete() {
        Lot lot = selectedLot();
        if (lot == null) {
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this, "Remove the purchase of " + lot.purchase().offers()
                        + " x " + lot.purchase().typeName() + "?", "Delete Purchase", JOptionPane.OK_CANCEL_OPTION);
        if (choice == JOptionPane.OK_OPTION) {
            appContext.lpPurchaseService.delete(lot.purchase().purchaseId());
            reload();
        }
    }
}
