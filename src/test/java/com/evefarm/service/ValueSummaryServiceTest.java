package com.evefarm.service;

import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.db.dao.AssetDao;
import com.evefarm.db.dao.CharacterDao;
import com.evefarm.db.dao.SnapshotDao;
import com.evefarm.db.dao.TypeCacheDao;
import com.evefarm.model.AssetEntry;
import com.evefarm.model.TypeInfo;
import com.evefarm.model.ValueSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

class ValueSummaryServiceTest {

    private static final long MALPAIS = 2124165849L;
    private static final long SABINA = 2122998456L;
    private static final long JITA = 60003760L;
    private static final int THANATOS = 23911;
    private static final int RIFTER = 587;
    private static final int HARDENER = 15705;
    private static final int TRITANIUM = 34;

    private ValueSummaryService values;

    @BeforeEach
    void setUp() {
        Database database = new Database(":memory:");
        MigrationRunner.run(database);
        CharacterDao characters = new CharacterDao(database);
        characters.upsert(MALPAIS, "Malpais Legate", List.of(), "owner");
        characters.upsert(SABINA, "Sabina Preda", List.of(), "owner");
        TypeCacheDao types = new TypeCacheDao(database);
        types.upsert(new TypeInfo(THANATOS, "Thanatos", "Carrier", "Ship", 1_000_000), 547, 6);
        types.upsert(new TypeInfo(RIFTER, "Rifter", "Frigate", "Ship", 27_289), 25, 6);
        types.upsert(new TypeInfo(HARDENER, "Corpus X-Type Thermal Armor Hardener", "Armor Hardener", "Module", 5),
                328, 7);
        types.upsert(new TypeInfo(TRITANIUM, "Tritanium", "Mineral", "Material", 0.01), 18, 4);
        AssetDao assets = new AssetDao(database);
        assets.replaceForCharacter(MALPAIS, List.of(
                asset(1, THANATOS, 1, 2_181_000_000.0),
                asset(2, HARDENER, 1, 348_400_000.0),
                asset(3, TRITANIUM, 900_000_000, 4.0)));
        assets.replaceForCharacter(SABINA, List.of(asset(4, RIFTER, 1, 500_000.0)));
        values = new ValueSummaryService(mock(SnapshotDao.class), assets, mock(CharacterService.class));
    }

    private static AssetEntry asset(long itemId, int typeId, long quantity, double unitPrice) {
        return new AssetEntry(itemId, typeId, quantity, JITA, "Hangar", quantity == 1, false, null, null, unitPrice,
                unitPrice * quantity);
    }

    @Test
    void theMostValuableItemsAreThoseWithTheHighestUnitPrice() {
        ValueSummary everyone = values.summarizeGrandTotal();

        assertEquals("Thanatos", everyone.bestAssetName(), "a pile of minerals worth more in total doesn't count");
        assertEquals(2_181_000_000.0, everyone.bestAssetValue());
        assertEquals("Thanatos", everyone.bestShipName());
        assertEquals("Corpus X-Type Thermal Armor Hardener", everyone.bestModuleName());
        assertEquals(348_400_000.0, everyone.bestModuleValue());
    }

    @Test
    void aCharacterSeesOnlyTheirOwnItems() {
        ValueSummary sabina = values.summarizeCharacter(SABINA);

        assertEquals("Rifter", sabina.bestAssetName());
        assertEquals("Rifter", sabina.bestShipName());
        assertEquals(500_000.0, sabina.bestShipValue());
        assertNull(sabina.bestModuleName());
        assertEquals(0.0, sabina.bestModuleValue());
    }
}
