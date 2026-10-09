package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.model.OrderCompetition;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderCompetitionDaoTest {

    @Test
    void savingACharactersResultsReplacesOnlyThatCharactersResults() {
        Database database = new Database(":memory:");
        MigrationRunner.run(database);
        OrderCompetitionDao dao = new OrderCompetitionDao(database);
        Instant now = Instant.parse("2026-10-09T12:00:00Z");
        dao.replaceForCharacter(1, List.of(new OrderCompetition(10, 5.0, true, now),
                new OrderCompetition(11, null, false, now)));
        dao.replaceForCharacter(2, List.of(new OrderCompetition(20, 7.0, false, now)));

        dao.replaceForCharacter(1, List.of(new OrderCompetition(12, 4.0, false, now)));

        assertEquals(Set.of(12L, 20L), dao.findCheckedSince(now).keySet());
        assertEquals(new OrderCompetition(12, 4.0, false, now), dao.findCheckedSince(now).get(12L));
    }
}
