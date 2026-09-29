package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.model.IndustryJobEntry;
import com.evefarm.model.IndustryJobRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IndustryJobDaoTest {

    private static final long PILOT = 90_000_001L;
    private static final long ALT = 90_000_002L;

    private IndustryJobDao jobs;

    @BeforeEach
    void setUp() {
        Database database = new Database(":memory:");
        MigrationRunner.run(database);
        CharacterDao characters = new CharacterDao(database);
        characters.upsert(PILOT, "Industrialist", null, List.of(), "owner-1");
        characters.upsert(ALT, "Alt", null, List.of(), "owner-2");
        jobs = new IndustryJobDao(database);
    }

    private static IndustryJobEntry job(long jobId, String status, String endDate) {
        return new IndustryJobEntry(jobId, 1, status, 1_000, 2_000, 10, 1_500.0, null, null,
                "2026-09-01T10:00:00Z", endDate);
    }

    private Map<Long, String> statuses() {
        return jobs.listRows(null).stream()
                .collect(Collectors.toMap(IndustryJobRow::jobId, IndustryJobRow::status));
    }

    @Test
    void finishedJobsStayAfterEsiStopsReturningThem() {
        jobs.saveForCharacter(PILOT, List.of(
                job(1, "delivered", "2026-06-01T10:00:00Z"),
                job(2, "cancelled", "2026-06-02T10:00:00Z"),
                job(3, "active", "2026-10-01T10:00:00Z")));

        jobs.saveForCharacter(PILOT, List.of(job(3, "active", "2026-10-01T10:00:00Z")));

        assertEquals(Map.of(1L, "delivered", 2L, "cancelled", 3L, "active"), statuses());
    }

    @Test
    void aJobThatFinishedIsUpdatedAndOneThatVanishedIsDropped() {
        jobs.saveForCharacter(PILOT, List.of(
                job(3, "active", "2026-09-20T10:00:00Z"),
                job(4, "paused", "2026-10-01T10:00:00Z")));

        jobs.saveForCharacter(PILOT, List.of(job(3, "delivered", "2026-09-20T10:00:00Z")));

        assertEquals(Map.of(3L, "delivered"), statuses());
    }

    @Test
    void savingOneCharacterLeavesTheOthersAlone() {
        jobs.saveForCharacter(ALT, List.of(job(5, "active", "2026-10-01T10:00:00Z")));

        jobs.saveForCharacter(PILOT, List.of());

        assertEquals(Map.of(5L, "active"), statuses());
    }

    @Test
    void theNewestJobsComeFirst() {
        jobs.saveForCharacter(PILOT, List.of(
                job(1, "delivered", "2026-06-01T10:00:00Z"),
                job(3, "active", "2026-10-01T10:00:00Z"),
                job(2, "delivered", "2026-08-01T10:00:00Z")));

        assertEquals(List.of(3L, 2L, 1L), jobs.listRows(null).stream().map(IndustryJobRow::jobId).toList());
    }
}
