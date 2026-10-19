package com.evefarm.ui;

import com.evefarm.AppContext;
import com.evefarm.ui.column.ColumnTableModel;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

final class DataTablePanelSupport<R> {

    private final AppContext appContext;
    private final String panelKey;
    private final ColumnTableModel<R> tableModel;
    private final JTable table;
    private final JPanel filterBarContainer;
    private final JLabel countLabel;
    private final String countNoun;
    private final Supplier<List<R>> rowsSupplier;

    private final AtomicInteger reloadGeneration = new AtomicInteger();
    private FilteredTable<R> filteredTable;
    private Runnable onRowsShown = () -> {
    };

    DataTablePanelSupport(AppContext appContext, String panelKey, ColumnTableModel<R> tableModel, JTable table,
                           JPanel filterBarContainer, JLabel countLabel, String countNoun,
                           Supplier<List<R>> rowsSupplier) {
        this.appContext = appContext;
        this.panelKey = panelKey;
        this.tableModel = tableModel;
        this.table = table;
        this.filterBarContainer = filterBarContainer;
        this.countLabel = countLabel;
        this.countNoun = countNoun;
        this.rowsSupplier = rowsSupplier;
    }

    void init() {
        filteredTable = new FilteredTable<>(appContext, panelKey, tableModel, table, filterBarContainer,
                () -> onRowsShown.run());
        reload();
    }

    void setOnRowsShown(Runnable onRowsShown) {
        this.onRowsShown = onRowsShown;
    }

    List<R> visibleRows() {
        return filteredTable.visibleRows();
    }

    void reload() {
        BackgroundLoader.load(reloadGeneration, rowsSupplier, this::applyRows, countNoun,
                () -> countLabel.setText("Couldn't load " + countNoun + " - see the log"));
    }

    private void applyRows(List<R> rows) {
        countLabel.setText(rows.size() + " " + countNoun);
        filteredTable.showRows(rows);
    }
}
