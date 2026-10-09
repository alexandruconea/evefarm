package com.evefarm.ui;

import com.evefarm.ui.column.ColumnDescriptions;
import com.formdev.flatlaf.FlatLaf;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;

public final class TableStyler {

    private static final int PACK_MARGIN_PX = 14;
    private static final int PACK_MAX_WIDTH_PX = 320;
    private static final int PACK_SAMPLE_ROWS = 300;

    public static void style(JTable table) {
        table.setRowHeight(26);
        table.setCellSelectionEnabled(true);
        table.setFillsViewportHeight(true);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        JTableHeader header = table.getTableHeader();
        if (header.getDefaultRenderer() instanceof JLabel headerLabel) {
            headerLabel.setHorizontalAlignment(SwingConstants.LEADING);
        }
        if (!(header.getDefaultRenderer() instanceof DescribedHeaderRenderer)) {
            header.setDefaultRenderer(new DescribedHeaderRenderer(header.getDefaultRenderer()));
        }

        DefaultTableCellRenderer stripedRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                             boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    c.setBackground(row % 2 == 0 ? t.getBackground() : stripeColor(t));
                }
                return c;
            }
        };
        stripedRenderer.putClientProperty("html.disable", Boolean.TRUE);
        table.setDefaultRenderer(Object.class, stripedRenderer);
        table.setDefaultRenderer(Long.class, stripedRenderer);
        table.setDefaultRenderer(Integer.class, stripedRenderer);
        table.setDefaultRenderer(Double.class, stripedRenderer);

        DefaultTableCellRenderer iconRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                             boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(t, null, false, false, row, column);
                setIcon(value instanceof Icon icon ? icon : null);
                setBackground(row % 2 == 0 ? t.getBackground() : stripeColor(t));
                return this;
            }
        };
        iconRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        table.setDefaultRenderer(Icon.class, iconRenderer);
        table.setDefaultRenderer(ImageIcon.class, iconRenderer);
    }

    static String columnDescription(JTable table, int column) {
        if (table == null || column < 0 || column >= table.getColumnCount()
                || !(table.getModel() instanceof ColumnDescriptions descriptions)) {
            return null;
        }
        String description = descriptions.columnDescription(table.convertColumnIndexToModel(column));
        return description == null || description.isBlank() ? null : description;
    }

    private record DescribedHeaderRenderer(TableCellRenderer base) implements TableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            Component component = base.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (component instanceof JComponent described) {
                described.setToolTipText(columnDescription(table, column));
            }
            return component;
        }
    }

    static Color stripeColor(JTable table) {
        Color themed = UIManager.getColor("Table.alternateRowColor");
        if (themed != null) {
            return themed;
        }
        Color base = table.getBackground();
        return FlatLaf.isLafDark() ? base.brighter() : new Color(247, 248, 250);
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
            int packed = Math.min(width + PACK_MARGIN_PX, PACK_MAX_WIDTH_PX);
            if (Icon.class.isAssignableFrom(table.getColumnClass(col))) {
                column.setMinWidth(packed);
                column.setMaxWidth(packed);
            }
            column.setPreferredWidth(packed);
        }
    }

    private TableStyler() {
    }
}
