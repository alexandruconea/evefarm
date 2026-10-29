package com.evefarm.ui.column;

import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.db.dao.TableColumnStateDao;
import com.evefarm.ui.TableStyler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColumnWidthMemoryTest {

    private static final ImageIcon ITEM_ICON = new ImageIcon(new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB));

    private TableColumnStateDao dao;

    @BeforeEach
    void setUp() {
        Database database = new Database(":memory:");
        MigrationRunner.run(database);
        dao = new TableColumnStateDao(database);
    }

    @Test
    void aColumnDraggedWiderKeepsThatWidthNextTimeInsteadOfFittingItsText() throws Exception {
        JTable table = itemsTable();
        ColumnWidthMemory.install(table, "items", dao);
        TableStyler.packColumns(table);
        drag(table, column(table, "name"), 260);

        JTable reopened = itemsTable();
        ColumnWidthMemory.install(reopened, "items", dao);
        TableStyler.packColumns(reopened);

        assertEquals(260, column(reopened, "name").getPreferredWidth());
        assertEquals(260, dao.findWidths("items").get("name"));
    }

    @Test
    void iconColumnsAreNeverSavedAndStayAsWideAsTheIcon() throws Exception {
        JTable table = itemsTable();
        ColumnWidthMemory.install(table, "items", dao);
        TableStyler.packColumns(table);
        drag(table, column(table, "name"), 260);

        TableColumn icon = column(table, "icon");
        assertFalse(dao.findWidths("items").containsKey("icon"));
        assertEquals(icon.getMinWidth(), icon.getMaxWidth());
        assertEquals(iconCellWidth(table), icon.getPreferredWidth());
    }

    @Test
    void resettingFitsTheColumnsToTheirTextAgain() throws Exception {
        JTable table = itemsTable();
        ColumnWidthMemory.install(table, "items", dao);
        TableStyler.packColumns(table);
        int fitted = column(table, "name").getPreferredWidth();
        drag(table, column(table, "name"), 260);

        ColumnWidthMemory memory = (ColumnWidthMemory) table.getClientProperty(ColumnWidthMemory.class);
        assertTrue(memory.resetItem().isEnabled());
        memory.resetItem().doClick();

        assertEquals(fitted, column(table, "name").getPreferredWidth());
        assertEquals(Map.of(), dao.findWidths("items"));
        assertFalse(memory.resetItem().isEnabled());
    }

    @Test
    void aColumnShownAgainFromChooseColumnsComesBackAtItsSavedWidth() {
        dao.saveWidths("items", Map.of("name", 210));
        JTable table = itemsTable();
        ColumnWidthMemory.install(table, "items", dao);

        TableColumn name = column(table, "name");
        table.removeColumn(name);
        TableColumn shownAgain = new TableColumn(name.getModelIndex());
        shownAgain.setIdentifier("name");
        table.addColumn(shownAgain);

        assertEquals(210, shownAgain.getPreferredWidth());
    }

    @Test
    void anIconColumnShownAgainIsStillAsWideAsTheIcon() {
        JTable table = itemsTable();
        TableColumn icon = column(table, "icon");
        table.removeColumn(icon);
        TableColumn shownAgain = new TableColumn(icon.getModelIndex());
        shownAgain.setIdentifier("icon");
        table.addColumn(shownAgain);

        assertEquals(iconCellWidth(table), shownAgain.getMinWidth());
        assertEquals(iconCellWidth(table), shownAgain.getMaxWidth());
    }

    @Test
    void anIconColumnShowsNoHeaderTextThatWouldBeCutOff() {
        JTable table = itemsTable();
        JTableHeader header = table.getTableHeader();
        int iconColumn = table.getColumnModel().getColumnIndex("icon");

        JLabel rendered = (JLabel) header.getDefaultRenderer().getTableCellRendererComponent(table,
                column(table, "icon").getHeaderValue(), false, false, -1, iconColumn);

        assertEquals("", rendered.getText());
    }

    private static JTable itemsTable() {
        ColumnTableModel<String> model = new ColumnTableModel<>(List.<ColumnDef<String>>of(
                new ColumnDef<>("icon", "Info", ImageIcon.class, value -> ITEM_ICON, "The item's icon"),
                new ColumnDef<>("name", "Name", String.class, value -> value, "The item's name"),
                new ColumnDef<>("price", "Price", String.class, value -> "1,000 ISK", "The price of one unit")));
        JTable table = new JTable(model);
        for (int i = 0; i < model.columns().size(); i++) {
            table.getColumnModel().getColumn(i).setIdentifier(model.columns().get(i).key());
        }
        TableStyler.style(table);
        model.setRows(List.of("Tritanium", "Pyerite"));
        TableStyler.packColumns(table);
        return table;
    }

    private static TableColumn column(JTable table, String key) {
        return table.getColumnModel().getColumn(table.getColumnModel().getColumnIndex(key));
    }

    private static int iconCellWidth(JTable table) {
        int iconColumn = table.getColumnModel().getColumnIndex("icon");
        return table.prepareRenderer(table.getCellRenderer(0, iconColumn), 0, iconColumn).getPreferredSize().width
                + table.getIntercellSpacing().width;
    }

    private static void drag(JTable table, TableColumn column, int width) throws Exception {
        JTableHeader header = table.getTableHeader();
        header.setResizingColumn(column);
        column.setWidth(width);
        header.dispatchEvent(new MouseEvent(header, MouseEvent.MOUSE_RELEASED, 0, 0, 1, 1, 1, false,
                MouseEvent.BUTTON1));
        SwingUtilities.invokeAndWait(() -> {
        });
    }
}
