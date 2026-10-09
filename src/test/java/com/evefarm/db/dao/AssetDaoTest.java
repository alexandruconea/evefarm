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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AssetDaoTest {

    private static final long PILOT = 90_000_001L;

    private Database database;
    private AssetDao assets;

    @BeforeEach
    void setUp() {
        database = new Database(":memory:");
        MigrationRunner.run(database);
        new CharacterDao(database).upsert(PILOT, "Hauler", List.of(), "owner");
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
    void stockCountsLooseItemsOfEveryCharacterInTheChosenSystem() {
        long alt = 90_000_002L;
        long jita = 60003760L;
        long amarr = 60008494L;
        new CharacterDao(database).upsert(alt, "Alt", List.of(), "owner");
        LocationCacheDao locations = new LocationCacheDao(database);
        locations.upsert(jita, "Jita IV - Moon 4", "station", 30000142L);
        locations.upsert(amarr, "Amarr VIII", "station", 30002187L);
        assets.replaceForCharacter(PILOT, List.of(
                new AssetEntry(1, 34, 1_000, jita, "Hangar", false, null, null, 5.0, 5_000.0),
                new AssetEntry(2, 34, 500, jita, "Unlocked", false, null, "Box", 5.0, 2_500.0),
                new AssetEntry(3, 3001, 1, jita, "HiSlot0", true, null, null, 9.0, 9.0),
                new AssetEntry(4, 3001, 1, jita, "Hangar", true, null, null, 9.0, 9.0),
                new AssetEntry(5, 34, 200, amarr, "Hangar", false, null, null, 5.0, 1_000.0)));
        assets.replaceForCharacter(alt, List.of(new AssetEntry(6, 34, 50, jita, "Cargo", false, null, null, 5.0,
                250.0)));

        assertEquals(Map.of(34, 1_550L), assets.usableStock(30000142L));
        assertEquals(Map.of(34, 1_750L), assets.usableStock(null));
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
