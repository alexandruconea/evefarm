package com.evefarm.ui.column;

import javax.swing.Icon;
import javax.swing.table.TableRowSorter;
import java.util.List;

public final class ColumnSorting {

    public static void install(TableRowSorter<?> sorter, ColumnTableModel<?> model) {
        List<? extends ColumnDef<?>> columns = model.columns();
        for (int i = 0; i < columns.size(); i++) {
            Class<?> type = columns.get(i).type();
            if (type == String.class) {
                sorter.setComparator(i, NumericAwareComparator.INSTANCE);
            } else if (Icon.class.isAssignableFrom(type)) {
                sorter.setSortable(i, false);
            }
        }
    }

    private ColumnSorting() {
    }
}
