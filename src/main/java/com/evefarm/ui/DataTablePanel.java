package com.evefarm.ui;

import com.evefarm.AppContext;
import com.evefarm.ui.column.ColumnTableModel;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import java.util.List;
import java.util.function.Supplier;

class DataTablePanel<R> extends TabPanel {

    final JTable table = new JTable();
    private final DataTablePanelSupport<R> support;

    DataTablePanel(AppContext appContext, String panelKey, ColumnTableModel<R> tableModel, String countNoun,
                   Supplier<List<R>> rows) {
        JPanel filterBarContainer = new JPanel();
        JLabel countLabel = new JLabel("0 " + countNoun);
        FilteredTable.layOut(this, filterBarContainer, new JScrollPane(table), countLabel);
        support = new DataTablePanelSupport<>(appContext, panelKey, tableModel, table, filterBarContainer,
                countLabel, countNoun, rows);
        support.init();
    }

    @Override
    public void onShown() {
        support.reload();
    }

    @Override
    public void refreshCharacterFilter() {
        support.reload();
    }
}
