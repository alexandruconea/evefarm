package com.evefarm.ui;

import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.table.DefaultTableModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
}
