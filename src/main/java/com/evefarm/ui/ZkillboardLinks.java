package com.evefarm.ui;

import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;

final class ZkillboardLinks {

    private static final int ICON_TEXT_GAP = 6;
    private static final int ICON_HIT_WIDTH = 1 + Icons.KILLBOARD.getIconWidth() + ICON_TEXT_GAP / 2;

    private ZkillboardLinks() {
    }

    static void install(JTable table) {
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                setIcon(value instanceof ZkillboardLink link && link.linked() ? Icons.KILLBOARD : null);
                setIconTextGap(ICON_TEXT_GAP);
                if (!isSelected) {
                    setBackground(row % 2 == 0 ? t.getBackground() : TableStyler.stripeColor(t));
                }
                return this;
            }
        };
        renderer.putClientProperty("html.disable", Boolean.TRUE);
        table.setDefaultRenderer(ZkillboardLink.class, renderer);

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                ZkillboardLink link = linkUnderIcon(table, e.getPoint());
                if (link != null && SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 1) {
                    open(table, link);
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                ZkillboardLink link = linkUnderIcon(table, e.getPoint());
                table.setCursor(link == null ? null : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                table.setToolTipText(link == null ? null : "Open " + link.name() + " on zKillboard");
            }
        };
        table.addMouseListener(mouse);
        table.addMouseMotionListener(mouse);
    }

    static ZkillboardLink linkUnderIcon(JTable table, Point point) {
        int row = table.rowAtPoint(point);
        int column = table.columnAtPoint(point);
        if (row < 0 || column < 0 || !(table.getValueAt(row, column) instanceof ZkillboardLink link) || !link.linked()) {
            return null;
        }
        Rectangle cell = table.getCellRect(row, column, false);
        return point.x - cell.x <= ICON_HIT_WIDTH ? link : null;
    }

    private static void open(JTable table, ZkillboardLink link) {
        try {
            Desktop.getDesktop().browse(URI.create(link.url()));
        } catch (Exception e) {
            JOptionPane.showMessageDialog(table, "Couldn't open the browser:\n" + link.url(),
                    "zKillboard", JOptionPane.WARNING_MESSAGE);
        }
    }
}
