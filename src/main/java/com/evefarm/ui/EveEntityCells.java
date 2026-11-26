package com.evefarm.ui;

import com.evefarm.service.ItemIconService;

import javax.swing.JTable;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeListener;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

final class EveEntityCells {

    private static final int ICON_TEXT_GAP = 6;

    private EveEntityCells() {
    }

    static void install(JTable table, ItemIconService icons) {
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                setIcon(value instanceof EveEntity entity ? icons.imageOrPlaceholder(entity.image()) : null);
                setIconTextGap(ICON_TEXT_GAP);
                if (!isSelected) {
                    setBackground(TableStyler.rowBackground(t, row));
                }
                return this;
            }
        };
        renderer.putClientProperty("html.disable", Boolean.TRUE);
        table.setDefaultRenderer(EveEntity.class, renderer);
        table.setRowHeight(Math.max(table.getRowHeight(), ItemIconService.RENDER_SIZE + 6));
    }

    static void installStandingsLinks(JTable table, Consumer<EveEntity> open) {
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                EveEntity entity = linkAt(table, e.getPoint());
                if (entity != null && SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 1) {
                    open.accept(entity);
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                EveEntity entity = linkAt(table, e.getPoint());
                table.setCursor(entity == null ? null : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                table.setToolTipText(entity == null ? null : "Show " + entity.name() + " in Standings");
            }

            @Override
            public void mouseExited(MouseEvent e) {
                table.setCursor(null);
            }
        };
        table.addMouseListener(mouse);
        table.addMouseMotionListener(mouse);
    }

    static void loadImagesOfShownRows(JTable table, ItemIconService icons) {
        Runnable loadShown = () -> loadImages(shownEntities(table), icons, table::repaint);
        ChangeListener onScroll = e -> loadShown.run();
        Runnable watchViewport = () -> {
            if (table.getParent() instanceof JViewport viewport
                    && !List.of(viewport.getChangeListeners()).contains(onScroll)) {
                viewport.addChangeListener(onScroll);
            }
        };
        watchViewport.run();
        table.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.PARENT_CHANGED) != 0) {
                watchViewport.run();
            }
        });
        table.getModel().addTableModelListener(e -> SwingUtilities.invokeLater(loadShown));
        if (table.getRowSorter() != null) {
            table.getRowSorter().addRowSorterListener(e -> SwingUtilities.invokeLater(loadShown));
        }
    }

    private static Stream<EveEntity> shownEntities(JTable table) {
        Rectangle shown = table.getVisibleRect();
        int first = table.rowAtPoint(shown.getLocation());
        if (first < 0) {
            return Stream.empty();
        }
        int last = table.rowAtPoint(new Point(shown.x, shown.y + shown.height - 1));
        int end = last < 0 ? table.getRowCount() - 1 : last;
        Stream.Builder<EveEntity> entities = Stream.builder();
        for (int row = first; row <= end; row++) {
            for (int column = 0; column < table.getColumnCount(); column++) {
                if (table.getValueAt(row, column) instanceof EveEntity entity) {
                    entities.add(entity);
                }
            }
        }
        return entities.build();
    }

    static void loadImages(Stream<EveEntity> entities, ItemIconService icons, Runnable onLoaded) {
        entities.map(EveEntity::image)
                .filter(image -> image != null)
                .distinct()
                .forEach(image -> icons.loadImageAsync(image, onLoaded));
    }

    private static EveEntity linkAt(JTable table, Point point) {
        int row = table.rowAtPoint(point);
        int column = table.columnAtPoint(point);
        if (row < 0 || column < 0 || !(table.getValueAt(row, column) instanceof EveEntity entity)
                || !entity.linked()) {
            return null;
        }
        Rectangle cell = table.getCellRect(row, column, false);
        Component shown = table.prepareRenderer(table.getCellRenderer(row, column), row, column);
        return point.x - cell.x <= shown.getPreferredSize().width ? entity : null;
    }
}
