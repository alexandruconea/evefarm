package com.evefarm.ui;

import org.junit.jupiter.api.Test;

import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import java.awt.Point;
import java.awt.Rectangle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZkillboardLinksTest {

    @Test
    void linksPointToTheRightZkillboardPages() {
        assertEquals("https://zkillboard.com/system/30000142/", ZkillboardLink.system("Jita", 30000142L).url());
        assertEquals("https://zkillboard.com/constellation/20000020/",
                ZkillboardLink.constellation("Kimotoro", 20000020L).url());
        assertEquals("https://zkillboard.com/region/10000002/", ZkillboardLink.region("The Forge", 10000002L).url());
    }

    @Test
    void theCellStillReadsAsThePlainNameForFiltersSortingAndCopying() {
        assertEquals("Jita", ZkillboardLink.system("Jita", 30000142L).toString());
        assertTrue(ZkillboardLink.system("amarr", 1L).compareTo(ZkillboardLink.system("Jita", 2L)) < 0);
        assertFalse(ZkillboardLink.system("Jita", null).linked(), "rows imported before the IDs existed get no icon");
    }

    @Test
    void onlyAClickOnTheIconOpensTheLink() {
        JTable table = new JTable(new DefaultTableModel(
                new Object[][]{{ZkillboardLink.system("Jita", 30000142L)}}, new Object[]{"System"}));
        table.setSize(300, 100);
        table.doLayout();
        Rectangle cell = table.getCellRect(0, 0, false);

        assertEquals("Jita", ZkillboardLinks.linkUnderIcon(table, new Point(cell.x + 8, cell.y + 5)).name());
        assertNull(ZkillboardLinks.linkUnderIcon(table, new Point(cell.x + 80, cell.y + 5)),
                "clicking the name selects the cell, it doesn't open the browser");
    }
}
