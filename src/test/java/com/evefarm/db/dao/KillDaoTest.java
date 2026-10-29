package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.model.KillDayTypeRow;
import com.evefarm.model.ParsedKill;
import com.evefarm.util.DateUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KillDaoTest {

    private static final long CHARACTER_ID = 2124165849L;
    private static final String FACTION = "Guristas";
    private static final String NPC = "Pithi Destructor";

    private KillDao dao;

    @BeforeEach
    void setUp() {
        Database database = new Database(":memory:");
        MigrationRunner.run(database);
        new CharacterDao(database).upsert(CHARACTER_ID, "Malpais Legate", List.of(), null);
        dao = new KillDao(database);
    }

    private void kill(Instant at) {
        dao.insertIfNew(CHARACTER_ID, new ParsedKill(at, NPC, FACTION, "Sosh", 120_000.0));
    }

    @Test
    void aDayCountsItsFirstSecondButNotMidnightOfTheNextDay() {
        LocalDate day = LocalDate.of(2026, 10, 28);
        Instant from = DateUtil.startOfDay(day);
        Instant to = DateUtil.endOfDay(day);
        kill(from);
        kill(from.plusSeconds(3600));
        kill(to);

        assertEquals(List.of(new KillDayTypeRow(day, FACTION, NPC, "Sosh", 2)),
                dao.listDayTypeRows(Set.of(CHARACTER_ID), Set.of(FACTION), Set.of(NPC), null, from, to));
    }
}
