package com.evefarm.ui;

import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.db.dao.TableColumnStateDao;
import com.evefarm.ui.column.ColumnDef;
import com.evefarm.ui.column.ColumnTableModel;
import com.evefarm.util.IskFormatter;
import org.junit.jupiter.api.Test;

import javax.swing.JTable;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SortableTablesTest {

    private record Bounty(String officer, double isk) {
    }

    private static final List<ColumnDef<Bounty>> COLUMNS = List.of(
            new ColumnDef<>("officer", "Officer", String.class, Bounty::officer, "The officer"),
            new ColumnDef<>("bounty", "Officer Bounty", String.class, r -> IskFormatter.format(r.isk()),
                    "The officer's bounty"));

    @Test
    void iskWrittenAsTextSortsByItsValueNotAlphabetically() {
        Database database = new Database(":memory:");
        MigrationRunner.run(database);
        ColumnTableModel<Bounty> model = new ColumnTableModel<>(COLUMNS);
        model.setRows(List.of(new Bounty("Estamel", 900_000_000), new Bounty("Vepas", 1_200_000_000),
                new Bounty("Tobias", 95_000_000)));
        JTable table = new JTable(model);

        SortableTables.withWidths(table, model, "officers", new TableColumnStateDao(database));
        table.getRowSorter().setSortKeys(List.of(new RowSorter.SortKey(1, SortOrder.ASCENDING)));

        assertEquals(List.of("Tobias", "Estamel", "Vepas"), List.of(
                table.getValueAt(0, 0), table.getValueAt(1, 0), table.getValueAt(2, 0)));
    }
}
