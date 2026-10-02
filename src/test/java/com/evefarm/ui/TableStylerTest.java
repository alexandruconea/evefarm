package com.evefarm.ui;

import org.junit.jupiter.api.Test;

import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.table.DefaultTableModel;
import java.awt.Component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
}
