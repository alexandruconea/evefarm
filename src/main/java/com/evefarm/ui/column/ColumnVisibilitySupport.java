package com.evefarm.ui.column;

import com.evefarm.db.dao.TableColumnStateDao;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.table.TableColumn;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ColumnVisibilitySupport<T> {

    private final JTable table;
    private final ColumnTableModel<T> model;
    private final String panelKey;
    private final TableColumnStateDao dao;
    private boolean applyingState;
    private boolean columnsMovedSinceRelease;

    private ColumnVisibilitySupport(JTable table, ColumnTableModel<T> model, String panelKey, TableColumnStateDao dao) {
        this.table = table;
        this.model = model;
        this.panelKey = panelKey;
        this.dao = dao;
    }

    public static <T> void install(JTable table, ColumnTableModel<T> model, String panelKey, TableColumnStateDao dao) {
        ColumnVisibilitySupport<T> support = new ColumnVisibilitySupport<>(table, model, panelKey, dao);
        support.applyPersistedOrDefaultState();
        support.installHeaderMenu();
        support.installReorderPersistence();
    }

    private void applyPersistedOrDefaultState() {
        List<String> keys = dao.find(panelKey).orElseGet(this::defaultKeys);
        applyState(keys);
    }

    private List<String> defaultKeys() {
        List<String> keys = new ArrayList<>();
        for (ColumnDef<T> def : model.columns()) {
            if (def.visibleByDefault()) {
                keys.add(def.key());
            }
        }
        return keys;
    }

    private void applyState(List<String> keys) {
        applyingState = true;
        try {
            Map<String, Integer> keyToModelIndex = new LinkedHashMap<>();
            for (int i = 0; i < model.columns().size(); i++) {
                keyToModelIndex.put(model.columns().get(i).key(), i);
            }
            while (table.getColumnModel().getColumnCount() > 0) {
                table.getColumnModel().removeColumn(table.getColumnModel().getColumn(0));
            }
            for (String key : keys) {
                Integer modelIndex = keyToModelIndex.get(key);
                if (modelIndex == null) {
                    continue;
                }
                TableColumn column = new TableColumn(modelIndex);
                column.setIdentifier(key);
                column.setHeaderValue(model.columns().get(modelIndex).label());
                table.addColumn(column);
            }
        } finally {
            applyingState = false;
        }
    }

    private List<String> currentViewKeys() {
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < table.getColumnModel().getColumnCount(); i++) {
            keys.add((String) table.getColumnModel().getColumn(i).getIdentifier());
        }
        return keys;
    }

    private void persistCurrentState() {
        dao.save(panelKey, currentViewKeys());
    }

    private void installHeaderMenu() {
        table.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowMenu(e);
            }

            private void maybeShowMenu(MouseEvent e) {
                if (!e.isPopupTrigger()) {
                    return;
                }
                JPopupMenu menu = new JPopupMenu();
                JMenuItem chooseColumns = new JMenuItem("Choose Columns...");
                chooseColumns.addActionListener(a -> openChooser());
                menu.add(chooseColumns);
                menu.show(e.getComponent(), e.getX(), e.getY());
            }
        });
    }

    private void openChooser() {
        Window owner = SwingUtilities.getWindowAncestor(table);
        List<String> result = ColumnChooserDialog.show(owner, model.columns(), currentViewKeys());
        if (result == null) {
            return;
        }
        applyState(result);
        persistCurrentState();
    }

    private void installReorderPersistence() {
        table.getColumnModel().addColumnModelListener(new TableColumnModelListener() {
            @Override
            public void columnMoved(TableColumnModelEvent e) {
                if (!applyingState && e.getFromIndex() != e.getToIndex()) {
                    columnsMovedSinceRelease = true;
                }
            }

            @Override
            public void columnAdded(TableColumnModelEvent e) {
            }

            @Override
            public void columnRemoved(TableColumnModelEvent e) {
            }

            @Override
            public void columnMarginChanged(ChangeEvent e) {
            }

            @Override
            public void columnSelectionChanged(ListSelectionEvent e) {
            }
        });
        table.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (columnsMovedSinceRelease) {
                    columnsMovedSinceRelease = false;
                    persistCurrentState();
                }
            }
        });
    }
}
