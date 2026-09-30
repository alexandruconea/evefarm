package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.model.MiningEntry;
import com.evefarm.model.MiningLedgerRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MiningLedgerDaoTest {

    private static final long MINER = 90_000_001L;
    private static final long VELDSPAR = 1230;
    private static final long JITA = 30000142L;

    private Database database;
    private CharacterDao characters;
    private MiningLedgerDao ledger;

    @BeforeEach
    void setUp() throws SQLException {
        database = new Database(":memory:");
        MigrationRunner.run(database);
        characters = new CharacterDao(database);
        characters.upsert(MINER, "Miner", null, List.of(), "owner");
        ledger = new MiningLedgerDao(database);
        try (Statement statement = database.connection().createStatement()) {
            statement.execute("INSERT INTO type_cache(type_id, name, group_id, group_name, category_id, category_name, "
                    + "volume, cached_at) VALUES (1230, 'Veldspar', 462, 'Veldspar', 25, 'Asteroid', 0.1, 'now')");
            statement.execute("INSERT INTO location_cache(location_id, name, location_type, system_id, cached_at) "
                    + "VALUES (30000142, 'Jita', 'solar_system', 30000142, 'now')");
        }
    }

    private static MiningEntry mined(String date, long quantity) {
        return new MiningEntry(date, JITA, (int) VELDSPAR, quantity);
    }

    @Test
    void todaysRowGrowsAndOlderDaysAreKept() {
        ledger.saveForCharacter(MINER, List.of(mined("2026-09-28", 10_000), mined("2026-09-29", 4_000)));
        ledger.saveForCharacter(MINER, List.of(mined("2026-09-29", 9_500)));

        List<MiningLedgerRow> rows = ledger.listRows(null);

        assertEquals(List.of("2026-09-29", "2026-09-28"), rows.stream().map(MiningLedgerRow::date).toList());
        assertEquals(List.of(9_500L, 10_000L), rows.stream().map(MiningLedgerRow::quantity).toList());
        assertEquals("Jita", rows.getFirst().systemName());
        assertEquals("Veldspar", rows.getFirst().oreName());
        assertEquals(0.1, rows.getFirst().unitVolume(), 1e-9);
    }

    @Test
    void aPeriodShowsOnlyItsDays() {
        ledger.saveForCharacter(MINER, List.of(mined("2026-08-01", 1), mined("2026-09-15", 2), mined("2026-09-29", 3)));

        assertEquals(List.of(3L, 2L), ledger.listRows("2026-09-01").stream().map(MiningLedgerRow::quantity).toList());
    }

    @Test
    void aRemovedCharactersMiningIsKeptButHidden() {
        ledger.saveForCharacter(MINER, List.of(mined("2026-09-29", 3)));

        characters.remove(MINER);
        assertEquals(List.of(), ledger.listRows(null));

        characters.upsert(MINER, "Miner", null, List.of(), "owner");
        assertEquals(1, ledger.listRows(null).size());
    }
}
