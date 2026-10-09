package com.evefarm.ui;

import com.evefarm.ui.column.ColumnDef;
import com.evefarm.ui.column.ColumnTableModel;
import org.junit.jupiter.api.Test;

import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableStylerTest {

    @Test
    void textWrittenByOtherPlayersIsShownAsIsAndNeverRenderedAsHtml() {
        String contractTitle = "<html><img src='http://example.com/track.png'>";
        JTable table = new JTable(new DefaultTableModel(new Object[][]{{contractTitle}}, new Object[]{"Title"}));
        TableStyler.style(table);

        JLabel cell = (JLabel) table.prepareRenderer(table.getCellRenderer(0, 0), 0, 0);

        assertEquals(contractTitle, cell.getText());
        assertNull(cell.getClientProperty(BasicHTML.propertyKey));
    }

    @Test
    void clickingSelectsOnlyThatCellNotTheWholeRow() {
        JTable table = new JTable(new DefaultTableModel(new Object[][]{{"a", "b", "c"}, {"d", "e", "f"}},
                new Object[]{"One", "Two", "Three"}));
        TableStyler.style(table);

        table.changeSelection(1, 2, false, false);

        assertTrue(table.isCellSelected(1, 2));
        assertFalse(table.isCellSelected(1, 0));
        assertFalse(table.isCellSelected(1, 1));
        assertEquals(1, table.getSelectedRow(), "code that asks for the selected row still gets it");
    }

    @Test
    void iconCellsNeverShowTheSelectionBackground() {
        JTable table = new JTable(new DefaultTableModel(new Object[][]{{Icons.INFO, "a"}}, new Object[]{"Info", "Name"}) {
            @Override
            public Class<?> getColumnClass(int column) {
                return column == 0 ? Icon.class : String.class;
            }
        });
        TableStyler.style(table);
        table.changeSelection(0, 0, false, false);

        Component iconCell = table.prepareRenderer(table.getCellRenderer(0, 0), 0, 0);

        assertEquals(table.getBackground(), iconCell.getBackground());
    }

    @Test
    void hoveringAColumnHeaderShowsWhatTheColumnMeansWhereverItIsMoved() {
        JTable table = new JTable(new ColumnTableModel<>(List.<ColumnDef<String>>of(
                new ColumnDef<>("name", "Name", String.class, value -> value, "The item's name"),
                new ColumnDef<>("price", "Price", String.class, value -> value, "The price of one unit"))));
        TableStyler.style(table);
        TableCellRenderer styled = table.getTableHeader().getDefaultRenderer();
        TableStyler.style(table);
        table.moveColumn(1, 0);

        assertSame(styled, table.getTableHeader().getDefaultRenderer());
        assertEquals("The price of one unit", headerTip(table, 0));
        assertEquals("The item's name", headerTip(table, 1));
    }

    @Test
    void tablesWithoutDescriptionsShowNoHeaderTip() {
        JTable table = new JTable(new DefaultTableModel(new Object[][]{{"a"}}, new Object[]{"Title"}));
        TableStyler.style(table);

        assertNull(headerTip(table, 0));
    }

    private static String headerTip(JTable table, int column) {
        JTableHeader header = table.getTableHeader();
        Rectangle cell = header.getHeaderRect(column);
        return header.getToolTipText(new MouseEvent(header, MouseEvent.MOUSE_MOVED, 0, 0, cell.x + 1, 1, 0, false));
    }
}
