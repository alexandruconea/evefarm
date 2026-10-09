package com.evefarm.ui;

import com.evefarm.ui.column.ColumnDef;
import com.evefarm.ui.column.ColumnTableModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColumnDescriptionsTest {

    @Test
    void everyColumnSaysWhatItShowsInPlainText() {
        List<ColumnTableModel<?>> models = List.of(new AbyssRunsTableModel(), new AgentsTableModel(),
                new AssetsTableModel(), new BuildOrBuyTableModel((typeId, build) -> {
                }), new ChainTableModel(), new ContractsTableModel(), new DecryptorOptionsTableModel(),
                new DropsTableModel(), new IndustryJobsTableModel(), new IndustryMaterialsTableModel(),
                new JournalTableModel(), new KillsTableModel(), new LpStoreTableModel(null),
                new MarketOrdersTableModel(), new MiningTableModel(null), new OfficersTableModel(),
                new PlanTableModel(), new ShoppingListTableModel(), new SpawnsTableModel(), new SpawnTableModel(),
                StandingsTableModel.factionsAndCorporations(), StandingsTableModel.agents(),
                new TransactionsTableModel());

        for (ColumnTableModel<?> model : models) {
            for (ColumnDef<?> column : model.columns()) {
                String where = model.getClass().getSimpleName() + "." + column.key();
                assertFalse(column.description() == null || column.description().isBlank(), where);
                assertTrue(column.description().chars().allMatch(c -> c < 128), where);
            }
        }
    }
}
