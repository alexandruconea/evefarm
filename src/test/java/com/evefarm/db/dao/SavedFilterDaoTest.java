package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SavedFilterDaoTest {

    private static final String SHIPS = "[{\"column\":\"category\",\"operator\":\"EQUALS\",\"value\":\"Ship\"}]";
    private static final String SHIPS_IN_JITA = "[{\"column\":\"category\",\"operator\":\"EQUALS\",\"value\":\"Ship\"},"
            + "{\"column\":\"location\",\"operator\":\"CONTAINS\",\"value\":\"Jita\"}]";

    private SavedFilterDao dao;

    @BeforeEach
    void setUp() {
        Database database = new Database(":memory:");
        MigrationRunner.run(database);
        dao = new SavedFilterDao(database);
    }

    @Test
    void savingTheSameNameAgainReplacesItsConditions() {
        dao.save("assets", "Ships", SHIPS);
        dao.save("assets", "Ships", SHIPS_IN_JITA);

        assertEquals(Optional.of(SHIPS_IN_JITA), dao.find("assets", "Ships"));
        assertEquals(List.of("Ships"), dao.listNames("assets"));
    }

    @Test
    void eachTableKeepsItsOwnFiltersInNameOrder() {
        dao.save("assets", "Ships", SHIPS);
        dao.save("assets", "Ammo", "[]");
        dao.save("journal", "Bounties", "[]");

        assertEquals(List.of("Ammo", "Ships"), dao.listNames("assets"));
        assertEquals(List.of("Bounties"), dao.listNames("journal"));
        assertEquals(Optional.empty(), dao.find("journal", "Ships"));
    }
}
