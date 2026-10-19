package com.evefarm.ui;

import com.evefarm.AppContext;
import com.evefarm.ui.column.ColumnCopySupport;
import com.evefarm.ui.column.ColumnSorting;
import com.evefarm.ui.column.ColumnTableModel;
import com.evefarm.ui.column.ColumnVisibilitySupport;
import com.evefarm.ui.filter.FilterBarPanel;

import javax.swing.GroupLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.LayoutStyle;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;

final class FilteredTable<R> {

    private final JTable table;
    private final ColumnTableModel<R> tableModel;
    private final TableRowSorter<ColumnTableModel<R>> sorter;
    private final FilterBarPanel filterBarPanel;
    private final Runnable onFiltered;

    FilteredTable(AppContext appContext, String panelKey, ColumnTableModel<R> tableModel, JTable table,
                  JPanel filterBarContainer) {
        this(appContext, panelKey, tableModel, table, filterBarContainer, () -> {
        });
    }

    FilteredTable(AppContext appContext, String panelKey, ColumnTableModel<R> tableModel, JTable table,
                  JPanel filterBarContainer, Runnable onFiltered) {
        this.table = table;
        this.tableModel = tableModel;
        this.onFiltered = onFiltered;
        table.setModel(tableModel);
        sorter = new TableRowSorter<>(tableModel);
        ColumnSorting.install(sorter, tableModel);
        table.setRowSorter(sorter);
        TableStyler.style(table);
        ColumnVisibilitySupport.install(table, tableModel, panelKey, appContext.tableColumnStateDao);
        ColumnCopySupport.install(table, tableModel);

        filterBarPanel = new FilterBarPanel(panelKey, tableModel.columnNames(), appContext.savedFilterDao);
        filterBarPanel.setOnFilterChanged(this::applyFilter);
        filterBarContainer.setLayout(new BorderLayout());
        filterBarContainer.add(filterBarPanel, BorderLayout.CENTER);
    }

    static void layOut(JPanel panel, JPanel filterBarContainer, JComponent table, JComponent bottom) {
        GroupLayout layout = new GroupLayout(panel);
        panel.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                    .addComponent(filterBarContainer, GroupLayout.DEFAULT_SIZE, 880, Short.MAX_VALUE)
                    .addComponent(table, GroupLayout.DEFAULT_SIZE, 880, Short.MAX_VALUE)
                    .addComponent(bottom, GroupLayout.DEFAULT_SIZE, 880, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(filterBarContainer, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(table, GroupLayout.DEFAULT_SIZE, 380, Short.MAX_VALUE)
                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(bottom, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
    }

    void showRows(List<R> rows) {
        tableModel.setRows(rows);
        TableStyler.packColumns(table);
        applyFilter();
    }

    List<R> visibleRows() {
        List<R> visible = new ArrayList<>(table.getRowCount());
        for (int row = 0; row < table.getRowCount(); row++) {
            visible.add(tableModel.rowAt(table.convertRowIndexToModel(row)));
        }
        return visible;
    }

    private void applyFilter() {
        sorter.setRowFilter(filterBarPanel.buildRowFilter());
        filterBarPanel.setRowCounts(table.getRowCount(), tableModel.getRowCount());
        onFiltered.run();
    }
}
