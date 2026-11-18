package com.evefarm.ui;

import com.evefarm.db.dao.TableColumnStateDao;
import com.evefarm.ui.column.ColumnSorting;
import com.evefarm.ui.column.ColumnTableModel;
import com.evefarm.ui.column.ColumnVisibilitySupport;
import com.evefarm.ui.column.ColumnWidthMemory;

import javax.swing.JTable;
import javax.swing.table.TableRowSorter;

final class SortableTables {

    static <M extends ColumnTableModel<?>> TableRowSorter<M> install(JTable table, M model) {
        TableRowSorter<M> sorter = new TableRowSorter<>(model);
        ColumnSorting.install(sorter, model);
        table.setRowSorter(sorter);
        return sorter;
    }

    static <M extends ColumnTableModel<?>> TableRowSorter<M> withWidths(JTable table, M model, String key,
                                                                        TableColumnStateDao dao) {
        TableRowSorter<M> sorter = install(table, model);
        TableStyler.style(table);
        ColumnWidthMemory.install(table, key, dao);
        return sorter;
    }

    static <M extends ColumnTableModel<?>> TableRowSorter<M> withColumnChooser(JTable table, M model, String key,
                                                                               TableColumnStateDao dao) {
        TableRowSorter<M> sorter = install(table, model);
        TableStyler.style(table);
        ColumnTableModel<?> columns = model;
        ColumnVisibilitySupport.install(table, columns, key, dao);
        return sorter;
    }

    private SortableTables() {
    }
}
