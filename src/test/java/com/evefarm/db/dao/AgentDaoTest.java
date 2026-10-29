package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.model.AgentRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentDaoTest {

    private AgentDao dao;

    @BeforeEach
    void setUp() {
        Database database = new Database(":memory:");
        MigrationRunner.run(database);
        dao = new AgentDao(database);
    }

    private static AgentRow agent(long id, String name, String system, Long systemId) {
        return new AgentRow(id, name, 1000035L, "Caldari Navy", 500001L, "Caldari State", "Security", "BasicAgent",
                4, false, 60003760L, "Jita IV - Moon 4 - Caldari Navy Assembly Plant", system, 0.95, "Kimotoro",
                "The Forge", systemId, 20000020L, 10000002L);
    }

    @Test
    void theCatalogComesBackInNameOrderWithItsEmptyFields() {
        AgentRow locator = new AgentRow(3014399L, "Yasonar Anoh", null, null, null, null, "Distribution", null, 5,
                true, null, null, null, null, null, null, null, null, null);
        AgentRow security = agent(3008416L, "Aakari Ebiahi", "Jita", 30000142L);

        dao.replaceAll(List.of(locator, security));

        assertEquals(List.of(security, locator), dao.listAll());
    }

    @Test
    void replacingTheCatalogDropsAgentsThatAreNoLongerInIt() {
        dao.replaceAll(List.of(agent(3008416L, "Aakari Ebiahi", "Jita", 30000142L)));
        AgentRow current = agent(3018920L, "Ichiro Saihara", "Perimeter", 30000144L);

        dao.replaceAll(List.of(current));

        assertEquals(List.of(current), dao.listAll());
    }

    @Test
    void onlyAgentsWithASystemNameButNoSystemIdNeedTheirLocationLookedUp() {
        AgentRow located = agent(3008416L, "Aakari Ebiahi", "Jita", 30000142L);
        AgentRow nowhere = agent(3018920L, "Ichiro Saihara", null, null);

        dao.replaceAll(List.of(located, nowhere));
        assertFalse(dao.hasAgentsWithoutLocationIds());

        dao.replaceAll(List.of(located, agent(3018920L, "Ichiro Saihara", "Perimeter", null)));
        assertTrue(dao.hasAgentsWithoutLocationIds());
    }
}
