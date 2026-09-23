package com.evefarm.ui;

import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;

public final class TableStyler {

    private static final int PACK_MARGIN_PX = 14;
    private static final int PACK_MAX_WIDTH_PX = 320;
    private static final int PACK_SAMPLE_ROWS = 300;

    public static void style(JTable table) {
        table.setRowHeight(22);
        table.setFillsViewportHeight(true);
        table.setShowGrid(true);
        table.setIntercellSpacing(new java.awt.Dimension(1, 1));

        boolean dark = com.formdev.flatlaf.FlatLaf.isLafDark();
        Color gridColor = dark ? new Color(70, 70, 70) : new Color(225, 225, 225);
        Color stripeColor = dark ? new Color(50, 50, 52) : new Color(245, 246, 248);
        table.setGridColor(gridColor);

        Font headerFont = table.getTableHeader().getFont();
        table.getTableHeader().setFont(headerFont.deriveFont(Font.BOLD));

        DefaultTableCellRenderer stripedRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                             boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    c.setBackground(row % 2 == 0 ? t.getBackground() : stripeColor);
                }
                return c;
            }
        };
        table.setDefaultRenderer(Object.class, stripedRenderer);
        table.setDefaultRenderer(Long.class, stripedRenderer);
        table.setDefaultRenderer(Integer.class, stripedRenderer);
        table.setDefaultRenderer(Double.class, stripedRenderer);
    }

    public static void packColumns(JTable table) {
        for (int col = 0; col < table.getColumnCount(); col++) {
            TableColumn column = table.getColumnModel().getColumn(col);

            TableCellRenderer headerRenderer = table.getTableHeader().getDefaultRenderer();
            Component headerComp = headerRenderer.getTableCellRendererComponent(
                    table, column.getHeaderValue(), false, false, -1, col);
            int width = headerComp.getPreferredSize().width;

            int rowCount = Math.min(table.getRowCount(), PACK_SAMPLE_ROWS);
            for (int row = 0; row < rowCount; row++) {
                TableCellRenderer cellRenderer = table.getCellRenderer(row, col);
                Component comp = table.prepareRenderer(cellRenderer, row, col);
                width = Math.max(width, comp.getPreferredSize().width);
            }
            column.setPreferredWidth(Math.min(width + PACK_MARGIN_PX, PACK_MAX_WIDTH_PX));
        }
    }

    private TableStyler() {
    }
}
