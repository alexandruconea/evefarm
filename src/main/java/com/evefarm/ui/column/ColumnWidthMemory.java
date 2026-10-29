package com.evefarm.ui.column;

import com.evefarm.db.dao.TableColumnStateDao;
import com.evefarm.ui.TableStyler;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.table.TableColumn;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ColumnWidthMemory {

    private final JTable table;
    private final String tableKey;
    private final TableColumnStateDao dao;
    private final Map<String, Integer> widths;
    private boolean resizedSinceRelease;

    private ColumnWidthMemory(JTable table, String tableKey, TableColumnStateDao dao) {
        this.table = table;
        this.tableKey = tableKey;
        this.dao = dao;
        this.widths = new LinkedHashMap<>(dao.findWidths(tableKey));
    }

    public static void install(JTable table, String tableKey, TableColumnStateDao dao) {
        ColumnWidthMemory memory = attach(table, tableKey, dao);
        table.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowMenu(e);
            }

            private void maybeShowMenu(MouseEvent e) {
                if (!e.isPopupTrigger()) {
                    return;
                }
                JPopupMenu menu = new JPopupMenu();
                menu.add(memory.resetItem());
                menu.show(e.getComponent(), e.getX(), e.getY());
            }
        });
    }

    static ColumnWidthMemory attach(JTable table, String tableKey, TableColumnStateDao dao) {
        ColumnWidthMemory memory = new ColumnWidthMemory(table, tableKey, dao);
        table.putClientProperty(ColumnWidthMemory.class, memory);
        for (int i = 0; i < table.getColumnModel().getColumnCount(); i++) {
            memory.applySavedWidth(table.getColumnModel().getColumn(i));
        }
        memory.installResizePersistence();
        return memory;
    }

    public static int savedWidth(JTable table, TableColumn column) {
        if (!(table.getClientProperty(ColumnWidthMemory.class) instanceof ColumnWidthMemory memory)
                || TableStyler.isIconColumn(table, column)) {
            return 0;
        }
        return memory.widths.getOrDefault(memory.keyOf(column), 0);
    }

    JMenuItem resetItem() {
        JMenuItem item = new JMenuItem("Reset Column Widths");
        item.setToolTipText("Fit the columns to what they show again, instead of the widths you dragged them to");
        item.setEnabled(!widths.isEmpty());
        item.addActionListener(e -> reset());
        return item;
    }

    private void reset() {
        widths.clear();
        dao.clearWidths(tableKey);
        TableStyler.packColumns(table);
    }

    private void applySavedWidth(TableColumn column) {
        int saved = savedWidth(table, column);
        if (saved > 0) {
            column.setPreferredWidth(saved);
        }
    }

    private String keyOf(TableColumn column) {
        if (table.getModel() instanceof ColumnTableModel<?> model && column.getModelIndex() < model.columns().size()) {
            return model.columns().get(column.getModelIndex()).key();
        }
        return String.valueOf(column.getIdentifier());
    }

    private void saveCurrentWidths() {
        for (int i = 0; i < table.getColumnModel().getColumnCount(); i++) {
            TableColumn column = table.getColumnModel().getColumn(i);
            if (!TableStyler.isIconColumn(table, column)) {
                widths.put(keyOf(column), column.getWidth());
            }
        }
        dao.saveWidths(tableKey, widths);
    }

    private void installResizePersistence() {
        table.getColumnModel().addColumnModelListener(new TableColumnModelListener() {
            @Override
            public void columnAdded(TableColumnModelEvent e) {
                applySavedWidth(table.getColumnModel().getColumn(e.getToIndex()));
            }

            @Override
            public void columnRemoved(TableColumnModelEvent e) {
            }

            @Override
            public void columnMoved(TableColumnModelEvent e) {
            }

            @Override
            public void columnMarginChanged(ChangeEvent e) {
                if (table.getTableHeader().getResizingColumn() != null) {
                    resizedSinceRelease = true;
                }
            }

            @Override
            public void columnSelectionChanged(ListSelectionEvent e) {
            }
        });
        table.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (resizedSinceRelease) {
                    resizedSinceRelease = false;
                    SwingUtilities.invokeLater(ColumnWidthMemory.this::saveCurrentWidths);
                }
            }
        });
    }
}
