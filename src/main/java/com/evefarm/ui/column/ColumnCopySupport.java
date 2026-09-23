package com.evefarm.ui.column;

import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

public final class ColumnCopySupport {

    public interface CustomCopy<T> {
        String copy(String columnKey, List<T> selectedRows);
    }

    public static <T> void install(JTable table, ColumnTableModel<T> model) {
        install(table, model, null);
    }

    public static <T> void install(JTable table, ColumnTableModel<T> model, CustomCopy<T> customCopy) {
        table.addMouseListener(new MouseAdapter() {
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
                int viewRow = table.rowAtPoint(e.getPoint());
                int viewColumn = table.columnAtPoint(e.getPoint());
                if (viewRow < 0 || viewColumn < 0) {
                    return;
                }
                if (!table.isRowSelected(viewRow)) {
                    table.setRowSelectionInterval(viewRow, viewRow);
                }
                int modelColumn = table.convertColumnIndexToModel(viewColumn);
                ColumnDef<T> column = model.columns().get(modelColumn);

                JPopupMenu menu = new JPopupMenu();
                JMenuItem copyItem = new JMenuItem("Copy " + column.label());
                copyItem.addActionListener(a -> copy(table, model, column.key(), modelColumn, customCopy));
                menu.add(copyItem);
                menu.show(e.getComponent(), e.getX(), e.getY());
            }
        });
    }

    private static <T> void copy(JTable table, ColumnTableModel<T> model, String columnKey, int modelColumn,
                                  CustomCopy<T> customCopy) {
        int[] selectedRows = table.getSelectedRows();
        if (selectedRows.length == 0) {
            return;
        }
        List<T> rows = new java.util.ArrayList<>(selectedRows.length);
        for (int viewRow : selectedRows) {
            rows.add(model.rowAt(table.convertRowIndexToModel(viewRow)));
        }

        String text = customCopy == null ? null : customCopy.copy(columnKey, rows);
        if (text == null) {
            StringBuilder sb = new StringBuilder();
            for (int viewRow : selectedRows) {
                Object value = model.getValueAt(table.convertRowIndexToModel(viewRow), modelColumn);
                sb.append(value == null ? "" : value.toString()).append('\n');
            }
            text = sb.toString();
        }

        if (text.isBlank()) {
            JOptionPane.showMessageDialog(table, "Nothing to copy for the selected row(s).",
                    "Copy", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }

    private ColumnCopySupport() {
    }
}
