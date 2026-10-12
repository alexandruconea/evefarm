package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.model.LoyaltyPointCheck;
import com.evefarm.model.LoyaltyPointEntry;
import com.evefarm.model.LoyaltyPointHistoryRow;
import com.evefarm.model.LpGain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoyaltyPointDaoTest {

    private static final long PILOT = 90_000_001L;
    private static final long GURISTAS = 1_000_127L;
    private static final long SANSHA = 1_000_162L;

    private static final Instant FIRST_READ = Instant.parse("2026-10-12T08:00:00Z");

    private Database database;
    private LoyaltyPointDao points;
    private int reads;

    @BeforeEach
    void setUp() {
        database = new Database(":memory:");
        MigrationRunner.run(database);
        new CharacterDao(database).upsert(PILOT, "Mission Runner", List.of(), "owner");
        points = new LoyaltyPointDao(database);
    }

    private void update(LoyaltyPointEntry... entries) {
        Instant dataFrom = FIRST_READ.plus(Duration.ofHours(reads++));
        points.replaceForCharacter(PILOT, List.of(entries), dataFrom, dataFrom.plus(Duration.ofHours(1)));
    }

    @Test
    void onlyChangesAreKeptInTheHistory() {
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
    void spendingEveryPointIsRecordedAsZero() {
        update(new LoyaltyPointEntry(GURISTAS, 10_000));
        update();

        LoyaltyPointHistoryRow latest = points.listHistory().getFirst();
        assertEquals(0, latest.loyaltyPoints());
        assertEquals(-10_000L, latest.change());
        assertEquals(List.of(), points.listForCharacter(PILOT));
    }

    @Test
    void theLpEarnedIsWhatWentUpSinceEveFarmReadItBefore() {
        update(new LoyaltyPointEntry(GURISTAS, 10_000));
        update(new LoyaltyPointEntry(GURISTAS, 14_500));
        update(new LoyaltyPointEntry(GURISTAS, 2_000));
        update(new LoyaltyPointEntry(GURISTAS, 2_000), new LoyaltyPointEntry(SANSHA, 800));
        update(new LoyaltyPointEntry(GURISTAS, 3_000), new LoyaltyPointEntry(SANSHA, 800));

        List<LpGain> gains = points.listGains();

        assertEquals(List.of(4_500L, 800L, 1_000L), gains.stream().map(LpGain::points).toList(),
                "the first read is where counting starts, and spending is not earning");
        assertEquals(List.of("Corporation #" + GURISTAS, "Corporation #" + SANSHA, "Corporation #" + GURISTAS),
                gains.stream().map(LpGain::corporationName).toList());
        assertEquals("Mission Runner", gains.getFirst().characterName());
        assertEquals(gains.get(1).seenAt(), gains.get(2).since(), "earned between the two reads");
        assertTrue(gains.stream().allMatch(gain -> gain.since().isBefore(gain.seenAt())));
    }

    @Test
    void theFirstLpACharacterEverEarnsCounts() {
        update();
        update(new LoyaltyPointEntry(GURISTAS, 500));

        assertEquals(List.of(500L), points.listGains().stream().map(LpGain::points).toList());
        assertEquals(points.listHistory().getFirst().recordedAt(), points.firstRecorded().get(PILOT).toString());
    }

    @Test
    void eachReadIsSavedAtTheTimeOfEvesDataWithTheNextRead() {
        Instant dataFrom = Instant.parse("2026-10-12T15:05:00Z");
        Instant nextRead = Instant.parse("2026-10-12T16:05:30Z");
        points.replaceForCharacter(PILOT, List.of(new LoyaltyPointEntry(GURISTAS, 10_000)), dataFrom, nextRead);

        assertEquals(new LoyaltyPointCheck(dataFrom, nextRead), points.checks().get(PILOT));
        assertEquals(dataFrom.toString(), points.listHistory().getFirst().recordedAt());
    }

    @Test
    void dataOlderThanWhatWasReadBeforeIsIgnored() {
        Instant dataFrom = Instant.parse("2026-10-12T15:05:00Z");
        points.replaceForCharacter(PILOT, List.of(new LoyaltyPointEntry(GURISTAS, 10_000)), dataFrom,
                dataFrom.plus(Duration.ofHours(1)));

        assertFalse(points.replaceForCharacter(PILOT, List.of(new LoyaltyPointEntry(GURISTAS, 9_000)),
                dataFrom.minus(Duration.ofMinutes(30)), dataFrom.plus(Duration.ofMinutes(30))));
        assertTrue(points.replaceForCharacter(PILOT, List.of(new LoyaltyPointEntry(GURISTAS, 10_000)),
                dataFrom.minusSeconds(1), dataFrom.plus(Duration.ofHours(1))), "the same data read again");

        assertEquals(10_000, points.listForCharacter(PILOT).getFirst().loyaltyPoints());
        assertEquals(1, points.listHistory().size());
    }

    @Test
    void historySavedBeforeTheReadsWereKeptCountsNewCorporationsAfterTheFirstDay() throws SQLException {
        insertOldHistory(GURISTAS, 10_000, "2026-09-29T10:00:00Z");
        insertOldHistory(GURISTAS, 12_000, "2026-09-30T10:00:00Z");
        insertOldHistory(SANSHA, 300, "2026-09-30T10:00:00Z");

        List<LpGain> gains = points.listGains();

        assertEquals(List.of(2_000L, 300L), gains.stream().map(LpGain::points).toList());
        assertNull(gains.getFirst().since(), "not known for history saved before");
        assertEquals(Instant.parse("2026-09-29T10:00:00Z"), points.firstRecorded().get(PILOT));
    }

    private void insertOldHistory(long corporationId, long loyaltyPoints, String recordedAt) throws SQLException {
        try (PreparedStatement ps = database.connection().prepareStatement(
                "INSERT INTO loyalty_point_history(character_id, corporation_id, loyalty_points, recorded_at) "
                        + "VALUES (?, ?, ?, ?)")) {
            ps.setLong(1, PILOT);
            ps.setLong(2, corporationId);
            ps.setLong(3, loyaltyPoints);
            ps.setString(4, recordedAt);
            ps.executeUpdate();
        }
    }
}
