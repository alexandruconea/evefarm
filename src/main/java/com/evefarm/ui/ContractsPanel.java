package com.evefarm.ui;

import com.evefarm.AppContext;

import javax.swing.SwingUtilities;
import javax.swing.table.TableColumn;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

public final class ContractsPanel extends javax.swing.JPanel {

    private static final String PANEL_KEY = "contracts";

    private final AppContext appContext;
    private final ContractsTableModel tableModel = new ContractsTableModel();
    private DataTablePanelSupport<com.evefarm.model.ContractRow> support;

    public ContractsPanel(AppContext appContext) {
        initComponents();
        this.appContext = appContext;
        postInit();
    }

    private void postInit() {
        support = new DataTablePanelSupport<>(appContext, PANEL_KEY, tableModel, table, filterBarContainer,
                contractCountLabel, "contracts", () -> appContext.contractService.getContractRows(null));
        support.init();
        showInfoColumn();
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int viewRow = table.rowAtPoint(e.getPoint());
                int viewColumn = table.columnAtPoint(e.getPoint());
                if (!SwingUtilities.isLeftMouseButton(e) || viewRow < 0 || viewColumn < 0) {
                    return;
                }
                boolean infoClick = isInfoColumn(viewColumn) && e.getClickCount() == 1;
                boolean rowDoubleClick = !isInfoColumn(viewColumn) && e.getClickCount() == 2;
                if (infoClick || rowDoubleClick) {
                    openContract(viewRow);
                }
            }
        });
        table.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                boolean overInfo = table.rowAtPoint(e.getPoint()) >= 0 && isInfoColumn(table.columnAtPoint(e.getPoint()));
                table.setCursor(overInfo ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : null);
                table.setToolTipText(overInfo ? "Show what's in this contract" : null);
            }
        });
    }

    private void showInfoColumn() {
        int infoModelIndex = infoModelIndex();
        for (int i = 0; i < table.getColumnCount(); i++) {
            if (table.convertColumnIndexToModel(i) == infoModelIndex) {
                return;
            }
        }
        TableColumn column = new TableColumn(infoModelIndex);
        column.setIdentifier(ContractsTableModel.INFO_COLUMN);
        column.setHeaderValue(tableModel.getColumnName(infoModelIndex));
        table.addColumn(column);
        table.moveColumn(table.getColumnCount() - 1, 0);
    }

    private int infoModelIndex() {
        for (int i = 0; i < tableModel.columns().size(); i++) {
            if (ContractsTableModel.INFO_COLUMN.equals(tableModel.columns().get(i).key())) {
                return i;
            }
        }
        return -1;
    }

    private boolean isInfoColumn(int viewColumn) {
        return viewColumn >= 0 && table.convertColumnIndexToModel(viewColumn) == infoModelIndex();
    }

    private void openContract(int viewRow) {
        com.evefarm.model.ContractRow contract = tableModel.rowAt(table.convertRowIndexToModel(viewRow));
        new ContractContentsDialog(SwingUtilities.getWindowAncestor(this), appContext, contract).setVisible(true);
    }

    public void onShown() {
        support.reload();
    }

    public void refreshCharacterFilter() {
        support.reload();
    }

    private javax.swing.JLabel contractCountLabel;
    private javax.swing.JPanel filterBarContainer;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JTable table;
    private void initComponents() {

        filterBarContainer = new javax.swing.JPanel();
        jScrollPane2 = new javax.swing.JScrollPane();
        table = new javax.swing.JTable();
        contractCountLabel = new javax.swing.JLabel();

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

        contractCountLabel.setText("0 contracts");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(filterBarContainer, javax.swing.GroupLayout.DEFAULT_SIZE, 880, Short.MAX_VALUE)
                    .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 880, Short.MAX_VALUE)
                    .addComponent(contractCountLabel))
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
                .addComponent(contractCountLabel)
                .addContainerGap())
        );
    }
}
