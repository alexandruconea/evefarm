package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TableColumnStateDaoTest {

    private TableColumnStateDao dao;

    @BeforeEach
    void setUp() {
        Database database = new Database(":memory:");
        MigrationRunner.run(database);
        dao = new TableColumnStateDao(database);
    }

    @Test
    void eachTableKeepsItsOwnColumnWidths() {
        dao.saveWidths("contracts", Map.of("title", 240, "status", 90));
        dao.saveWidths("journal", Map.of("amount", 120));

        assertEquals(Map.of("title", 240, "status", 90), dao.findWidths("contracts"));
        assertEquals(Map.of("amount", 120), dao.findWidths("journal"));
        assertEquals(Map.of(), dao.findWidths("assets"));
    }

    @Test
    void savingWidthsAgainReplacesThemAndClearingForgetsThem() {
        dao.saveWidths("contracts", Map.of("title", 240, "status", 90));
        dao.saveWidths("contracts", Map.of("title", 180));

        assertEquals(Map.of("title", 180), dao.findWidths("contracts"));

        dao.clearWidths("contracts");

        assertEquals(Map.of(), dao.findWidths("contracts"));
    }

    @Test
    void widthsDoNotTouchTheChosenColumns() {
        dao.save("contracts", List.of("info", "title"));
        dao.saveWidths("contracts", Map.of("title", 240));
        dao.clearWidths("contracts");

        assertEquals(Optional.of(List.of("info", "title")), dao.find("contracts"));
    }
}
