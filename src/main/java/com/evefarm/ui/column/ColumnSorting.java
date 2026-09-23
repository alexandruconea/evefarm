package com.evefarm.ui.column;

import javax.swing.table.TableRowSorter;
import java.util.List;

public final class ColumnSorting {

    public static void installNumericAwareComparators(TableRowSorter<?> sorter, ColumnTableModel<?> model) {
        List<? extends ColumnDef<?>> columns = model.columns();
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).type() == String.class) {
                sorter.setComparator(i, NumericAwareComparator.INSTANCE);
            }
        }
    }

    private ColumnSorting() {
    }
}
