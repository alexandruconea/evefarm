package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.model.TrackerSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SnapshotDaoTest {

    private static final long PILOT = 1L;
    private static final long ALT = 2L;
    private static final Instant DAY_1 = Instant.parse("2026-10-01T12:00:00Z");
    private static final Instant DAY_2 = Instant.parse("2026-10-02T12:00:00Z");
    private static final Instant DAY_3 = Instant.parse("2026-10-03T12:00:00Z");

    private SnapshotDao snapshots;
    private CharacterDao characters;

    @BeforeEach
    void setUp() {
        Database database = new Database(":memory:");
        MigrationRunner.run(database);
        snapshots = new SnapshotDao(database);
        characters = new CharacterDao(database);
        characters.upsert(PILOT, "Pilot", List.of(), "owner");
        characters.upsert(ALT, "Alt", List.of(), "owner");
    }

    private static TrackerSnapshot snapshot(long characterId, Instant at, double wallet) {
        return TrackerSnapshot.of(characterId, at, wallet, 2_000, 0, 300, 0, 0, 0, 0, 0, 5_000_000, 40, 0);
    }

    @Test
    void theLatestSnapshotOfACharacterIsFound() {
        snapshots.insert(snapshot(PILOT, DAY_1, 100));
        snapshots.insert(snapshot(PILOT, DAY_3, 300));
        snapshots.insert(snapshot(ALT, DAY_2, 999));

        assertEquals(Optional.of(snapshot(PILOT, DAY_3, 300)), snapshots.findLatest(PILOT));
        assertEquals(Optional.empty(), snapshots.findLatest(99L));
    }

    @Test
    void aSnapshotTakenAgainAtTheSameMomentReplacesTheFirstOne() {
        snapshots.insert(snapshot(PILOT, DAY_1, 100));
        snapshots.insert(snapshot(PILOT, DAY_1, 150));

        assertEquals(List.of(snapshot(PILOT, DAY_1, 150)), snapshots.listBetween(Set.of(PILOT), DAY_1, DAY_1));
    }

    @Test
    void aRangeListsTheChosenCharactersInTimeOrderWithoutRemovedOnes() {
        snapshots.insert(snapshot(PILOT, DAY_3, 300));
        snapshots.insert(snapshot(PILOT, DAY_1, 100));
        snapshots.insert(snapshot(ALT, DAY_2, 200));

        assertEquals(List.of(snapshot(PILOT, DAY_1, 100), snapshot(ALT, DAY_2, 200)),
                snapshots.listBetween(null, DAY_1, DAY_2));
        assertEquals(List.of(snapshot(PILOT, DAY_1, 100), snapshot(PILOT, DAY_3, 300)),
                snapshots.listBetween(Set.of(PILOT), DAY_1, DAY_3));

        characters.remove(ALT);
        snapshots.delete(PILOT, DAY_1);

        assertEquals(List.of(snapshot(PILOT, DAY_3, 300)), snapshots.listBetween(null, DAY_1, DAY_3));
    }
}
