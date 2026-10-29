package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.model.Ore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OreDaoTest {

    private static final int TRITANIUM = 34;
    private static final int PYERITE = 35;

    private OreDao dao;

    @BeforeEach
    void setUp() {
        Database database = new Database(":memory:");
        MigrationRunner.run(database);
        dao = new OreDao(database);
    }

    @Test
    void oresComeBackWithTheirMineralsAndCompressedForm() {
        Ore veldspar = new Ore(1230, 100, 62516, Map.of(TRITANIUM, 400L));
        Ore scordite = new Ore(1228, 100, null, Map.of(TRITANIUM, 150L, PYERITE, 90L));
        Ore gas = new Ore(30375, 10, null, Map.of());

        dao.replaceAll(List.of(veldspar, scordite, gas));

        assertEquals(3, dao.count());
        assertEquals(Map.of(1230, veldspar, 1228, scordite, 30375, gas), dao.loadAll());
    }

    @Test
    void aNewCatalogLeavesNoMineralsFromTheOldOne() {
        dao.replaceAll(List.of(new Ore(1230, 100, 62516, Map.of(TRITANIUM, 400L)),
                new Ore(1228, 100, null, Map.of(TRITANIUM, 150L, PYERITE, 90L))));
        Ore rebalanced = new Ore(1230, 100, 62516, Map.of(TRITANIUM, 415L));

        dao.replaceAll(List.of(rebalanced));

        assertEquals(1, dao.count());
        assertEquals(Map.of(1230, rebalanced), dao.loadAll());
    }
}
