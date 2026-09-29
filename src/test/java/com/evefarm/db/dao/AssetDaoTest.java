package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.model.AssetEntry;
import com.evefarm.model.AssetRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AssetDaoTest {

    private static final long PILOT = 90_000_001L;

    private Database database;
    private AssetDao assets;

    @BeforeEach
    void setUp() {
        database = new Database(":memory:");
        MigrationRunner.run(database);
        new CharacterDao(database).upsert(PILOT, "Hauler", null, List.of(), "owner");
        assets = new AssetDao(database);
    }

    private static AssetEntry asset(long itemId, long quantity) {
        return new AssetEntry(itemId, 34, quantity, 60003760L, "Hangar", false, null, null, 5.0, 5.0 * quantity);
    }

    @Test
    void eachMonthKeepsTheLastAssetsSavedInIt() throws SQLException {
        assets.replaceForCharacter(PILOT, List.of(asset(1, 100)));
        assets.replaceForCharacter(PILOT, List.of(asset(1, 250), asset(2, 10)));
        String month = Instant.now().toString().substring(0, 7);

        assertEquals(List.of(month), assets.listArchivedMonths().stream().map(AssetDao.ArchivedMonth::month).toList());
        assertEquals(List.of(250L, 10L),
                assets.listArchivedRows(month, null).stream().map(AssetRow::quantity).toList());
    }

    @Test
    void earlierMonthsAreKeptWhenTheAssetsChange() throws SQLException {
        assets.replaceForCharacter(PILOT, List.of(asset(1, 100)));
        try (Statement statement = database.connection().createStatement()) {
            statement.execute("UPDATE asset_archive SET month = '2024-05'");
        }

        assets.replaceForCharacter(PILOT, List.of(asset(1, 7)));

        assertEquals(2, assets.listArchivedMonths().size());
        assertEquals(List.of(100L), assets.listArchivedRows("2024-05", null).stream().map(AssetRow::quantity).toList());
        assertEquals(List.of(7L), assets.listRows(null).stream().map(AssetRow::quantity).toList());
    }
}
