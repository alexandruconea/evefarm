package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.model.LoyaltyPointEntry;
import com.evefarm.model.LoyaltyPointHistoryRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LoyaltyPointDaoTest {

    private static final long PILOT = 90_000_001L;
    private static final long GURISTAS = 1_000_127L;
    private static final long SANSHA = 1_000_162L;

    private LoyaltyPointDao points;

    @BeforeEach
    void setUp() {
        Database database = new Database(":memory:");
        MigrationRunner.run(database);
        new CharacterDao(database).upsert(PILOT, "Mission Runner", List.of(), "owner");
        points = new LoyaltyPointDao(database);
    }

    private void update(LoyaltyPointEntry... entries) throws InterruptedException {
        points.replaceForCharacter(PILOT, List.of(entries));
        Thread.sleep(2);
    }

    @Test
    void onlyChangesAreKeptInTheHistory() throws InterruptedException {
        update(new LoyaltyPointEntry(GURISTAS, 10_000));
        update(new LoyaltyPointEntry(GURISTAS, 10_000));
        update(new LoyaltyPointEntry(GURISTAS, 14_500), new LoyaltyPointEntry(SANSHA, 800));

        List<LoyaltyPointHistoryRow> history = points.listHistory();

        assertEquals(3, history.size());
        assertEquals(List.of(4_500L, 10_000L), history.stream()
                .filter(row -> row.corporationId() == GURISTAS)
                .map(row -> row.change() == null ? row.loyaltyPoints() : row.change()).toList());
        assertNull(history.stream().filter(row -> row.corporationId() == SANSHA).findFirst().orElseThrow().change());
    }

    @Test
    void spendingEveryPointIsRecordedAsZero() throws InterruptedException {
        update(new LoyaltyPointEntry(GURISTAS, 10_000));
        update();

        LoyaltyPointHistoryRow latest = points.listHistory().getFirst();
        assertEquals(0, latest.loyaltyPoints());
        assertEquals(-10_000L, latest.change());
        assertEquals(List.of(), points.listForCharacter(PILOT));
    }
}
