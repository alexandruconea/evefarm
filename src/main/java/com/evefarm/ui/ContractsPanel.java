package com.evefarm.ui;

import com.evefarm.AppContext;
import com.evefarm.model.ContractRow;

import javax.swing.SwingUtilities;
import javax.swing.table.TableColumn;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

public final class ContractsPanel extends DataTablePanel<ContractRow> {

    private final AppContext appContext;
    private final ContractsTableModel tableModel;

    public ContractsPanel(AppContext appContext) {
        this(appContext, new ContractsTableModel());
    }

    private ContractsPanel(AppContext appContext, ContractsTableModel tableModel) {
        super(appContext, "contracts", tableModel, "contracts", appContext.contractService::getContractRows);
        this.appContext = appContext;
        this.tableModel = tableModel;
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
        ContractRow contract = tableModel.rowAt(table.convertRowIndexToModel(viewRow));
        new ContractContentsDialog(SwingUtilities.getWindowAncestor(this), appContext, contract).setVisible(true);
    }
}
