package com.evefarm.ui;

import com.evefarm.ui.column.ColumnDef;
import com.evefarm.ui.column.ColumnTableModel;
import com.evefarm.ui.column.Isk;
import org.junit.jupiter.api.Test;

import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
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
    void amountsStandRightWithTheUnitInTheHeaderAndGainsAndLossesInColor() {
        ColumnTableModel<Double> model = new ColumnTableModel<>(List.of(
                new ColumnDef<>("what", "What", String.class, value -> "Bounty Prizes", "What it was"),
                new ColumnDef<>("amount", "Amount", Isk.class, Isk::signed, "The ISK that came in or went out")));
        model.setRows(List.of(26_378_176.31, -88_695.0));
        JTable table = new JTable(model);
        TableStyler.style(table);

        JLabel gain = (JLabel) table.prepareRenderer(table.getCellRenderer(0, 1), 0, 1);
        assertEquals("26,378,176.31", gain.getText());
        assertEquals(SwingConstants.RIGHT, gain.getHorizontalAlignment());
        assertEquals(TableStyler.gainColor(), gain.getForeground());
        JLabel loss = (JLabel) table.prepareRenderer(table.getCellRenderer(1, 1), 1, 1);
        assertEquals(TableStyler.lossColor(), loss.getForeground());
        JLabel text = (JLabel) table.prepareRenderer(table.getCellRenderer(0, 0), 0, 0);
        assertEquals(SwingConstants.LEADING, text.getHorizontalAlignment());
        assertEquals("Amount (ISK)", TableStyler.withUnit(table, 1, "Amount"));
        assertEquals("What", TableStyler.withUnit(table, 0, "What"));
    }

    @Test
    void textThatIsANumberStandsRightToo() {
        for (String number : List.of("1,234", "234,000.00 m3", "5.2%", "-0.23", "12,000 LP", "4,000 ISK/LP")) {
            assertTrue(TableStyler.looksLikeNumber(number), number);
        }
        for (String text : List.of("2026-10-01 Thu", "Malpais Legate", "T3 Fierce", "1h 20m", "", "Yes")) {
            assertFalse(TableStyler.looksLikeNumber(text), text);
        }
    }

    @Test
    void columnsAreSeparatedByLinesAndRowsByStripes() {
        JTable table = new JTable(new DefaultTableModel(new Object[][]{{"a", "b"}}, new Object[]{"One", "Two"}));
        TableStyler.style(table);

        assertTrue(table.getShowVerticalLines());
        assertFalse(table.getShowHorizontalLines());
        assertEquals(1, table.getIntercellSpacing().width);
        assertEquals(TableStyler.gridColor(), table.getGridColor());
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
