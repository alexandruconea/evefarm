package com.evefarm.ui;

import com.evefarm.AppContext;
import com.evefarm.model.TransactionRow;

import javax.swing.GroupLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.LayoutStyle;

public final class TransactionsPanel extends JPanel {

    private static final String PANEL_KEY = "transactions";

    private final AppContext appContext;
    private final TransactionsTableModel tableModel = new TransactionsTableModel();
    private DataTablePanelSupport<TransactionRow> support;

    public TransactionsPanel(AppContext appContext) {
        initComponents();
        this.appContext = appContext;
        postInit();
    }

    private void postInit() {
        support = new DataTablePanelSupport<>(appContext, PANEL_KEY, tableModel, table, filterBarContainer,
                transactionCountLabel, "transactions", () -> appContext.transactionService.getTransactionRows(null));
        support.init();
    }

    public void onShown() {
        support.reload();
    }

    public void refreshCharacterFilter() {
        support.reload();
    }

    private JPanel filterBarContainer;
    private JScrollPane jScrollPane2;
    private JTable table;
    private JLabel transactionCountLabel;

    private void initComponents() {

        filterBarContainer = new JPanel();
        jScrollPane2 = new JScrollPane();
        table = new JTable();
        transactionCountLabel = new JLabel();

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

        transactionCountLabel.setText("0 transactions");

        GroupLayout layout = new GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                    .addComponent(filterBarContainer, GroupLayout.DEFAULT_SIZE, 880, Short.MAX_VALUE)
                    .addComponent(jScrollPane2, GroupLayout.DEFAULT_SIZE, 880, Short.MAX_VALUE)
                    .addComponent(transactionCountLabel))
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
                .addComponent(transactionCountLabel)
                .addContainerGap())
        );
    }
}
