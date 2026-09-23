package com.evefarm.ui.column;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public class ColumnTableModel<T> extends AbstractTableModel {

    private final List<ColumnDef<T>> columns;
    private List<T> rows = new ArrayList<>();

    public ColumnTableModel(List<ColumnDef<T>> columns) {
        this.columns = columns;
    }

    public List<ColumnDef<T>> columns() {
        return columns;
    }

    public List<String> columnNames() {
        return columns.stream().map(ColumnDef::label).toList();
    }

    public void setRows(List<T> rows) {
        this.rows = rows;
        fireTableDataChanged();
    }

    public List<T> rows() {
        return rows;
    }

    public T rowAt(int index) {
        return rows.get(index);
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return columns.size();
    }

    @Override
    public String getColumnName(int column) {
        return columns.get(column).label();
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return columns.get(columnIndex).type();
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        return columns.get(columnIndex).getter().apply(rows.get(rowIndex));
    }
}
