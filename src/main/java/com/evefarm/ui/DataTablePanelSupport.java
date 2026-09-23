package com.evefarm.ui;

import com.evefarm.AppContext;
import com.evefarm.ui.column.ColumnCopySupport;
import com.evefarm.ui.column.ColumnSorting;
import com.evefarm.ui.column.ColumnTableModel;
import com.evefarm.ui.column.ColumnVisibilitySupport;
import com.evefarm.ui.filter.FilterBarPanel;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
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
    private FilterBarPanel filterBarPanel;
    private TableRowSorter<ColumnTableModel<R>> sorter;

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
        table.setModel(tableModel);
        sorter = new TableRowSorter<>(tableModel);
        ColumnSorting.installNumericAwareComparators(sorter, tableModel);
        table.setRowSorter(sorter);
        TableStyler.style(table);
        ColumnVisibilitySupport.install(table, tableModel, panelKey, appContext.tableColumnStateDao);
        ColumnCopySupport.install(table, tableModel);

        filterBarPanel = new FilterBarPanel(panelKey, tableModel.columnNames(), appContext.savedFilterDao);
        filterBarPanel.setOnFilterChanged(this::applyFilter);
        filterBarContainer.setLayout(new BorderLayout());
        filterBarContainer.add(filterBarPanel, BorderLayout.CENTER);

        reload();
    }

    void reload() {
        BackgroundLoader.load(reloadGeneration, rowsSupplier, this::applyRows, countNoun,
                () -> countLabel.setText("Couldn't load " + countNoun + " - see the log"));
    }

    private void applyRows(List<R> rows) {
        tableModel.setRows(rows);
        countLabel.setText(rows.size() + " " + countNoun);
        TableStyler.packColumns(table);
        applyFilter();
    }

    private void applyFilter() {
        sorter.setRowFilter(filterBarPanel.buildRowFilter());
        filterBarPanel.setRowCounts(table.getRowCount(), tableModel.getRowCount());
    }
}
