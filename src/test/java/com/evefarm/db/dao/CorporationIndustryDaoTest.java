package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.model.AssetEntry;
import com.evefarm.model.BlueprintEntry;
import com.evefarm.model.BlueprintRow;
import com.evefarm.model.Corporation;
import com.evefarm.model.IndustryJobEntry;
import com.evefarm.model.IndustryJobRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorporationIndustryDaoTest {

    private static final long CORPORATION = 98_000_001L;
    private static final long JITA = 60003760L;

    private Database database;
    private CorporationDao corporations;
    private CorporationIndustryDao dao;

    @BeforeEach
    void setUp() {
        database = new Database(":memory:");
        MigrationRunner.run(database);
        corporations = new CorporationDao(database);
        corporations.saveCorporation(new Corporation(CORPORATION, "Deep Core Mining", "DCM"));
        dao = new CorporationIndustryDao(database);
    }

    private static IndustryJobEntry job(long jobId, String status) {
        return new IndustryJobEntry(jobId, 1, status, 1_000, 2_000, 10, 1_500.0, JITA, JITA,
                "2026-11-27T10:00:00Z", "2026-11-29T10:00:00Z");
    }

    @Test
    void jobsShowTheCorporationAndWhoStartedThem() {
        new EntityNameCacheDao(database).upsert(90, "Malpais Legate", "character");
        dao.saveJobs(CORPORATION, List.of(new CorporationIndustryDao.Job(job(1, "active"), 90L),
                new CorporationIndustryDao.Job(job(2, "delivered"), null)));

        List<IndustryJobRow> rows = dao.listJobRows();

        assertEquals(List.of("Deep Core Mining [DCM] · Malpais Legate", "Deep Core Mining [DCM]"),
                rows.stream().sorted((a, b) -> Long.compare(a.jobId(), b.jobId()))
                        .map(IndustryJobRow::characterName).toList());
        assertTrue(rows.stream().allMatch(IndustryJobRow::corporation));
    }

    @Test
    void finishedJobsStayWhenEsiNoLongerListsThem() {
        dao.saveJobs(CORPORATION, List.of(new CorporationIndustryDao.Job(job(1, "active"), null),
                new CorporationIndustryDao.Job(job(2, "delivered"), null)));

        dao.saveJobs(CORPORATION, List.of());

        assertEquals(List.of(2L), dao.listJobRows().stream().map(IndustryJobRow::jobId).toList());
    }

    @Test
    void blueprintsShowTheirHangarAndWhereTheyAre() {
        corporations.saveDivisions(CORPORATION, CorporationDao.HANGAR, Map.of(2, "Ships"));
        corporations.replaceAssets(CORPORATION, List.of(
                new AssetEntry(11, 1_000, 1, JITA, "CorpSAG2", true, false, null, null, 0, 0),
                new AssetEntry(12, 1_001, 1, JITA, "Unlocked", true, true, null, "Blueprint Box", 0, 0)));
        dao.replaceBlueprints(CORPORATION, List.of(
                new BlueprintEntry(11, 1_000, 500L, "CorpSAG2", -1, 10, 20, -1),
                new BlueprintEntry(12, 1_001, 13L, "Unlocked", -2, 2, 4, 15)));

        List<BlueprintRow> rows = dao.listBlueprintRows();

        BlueprintRow original = rows.stream().filter(row -> row.itemId() == 11).findFirst().orElseThrow();
        BlueprintRow copy = rows.stream().filter(row -> row.itemId() == 12).findFirst().orElseThrow();
        assertEquals("Hangar 2: Ships", original.locationFlag());
        assertFalse(original.copy());
        assertNull(original.containerName());
        assertTrue(copy.copy());
        assertEquals(15, copy.runs());
        assertEquals("Blueprint Box", copy.containerName());
        assertEquals("Location #" + JITA, copy.locationName(), "the station of the asset, not the box");
        assertEquals("Deep Core Mining [DCM]", copy.ownerName());
    }

    @Test
    void blueprintsGoneFromEsiAreGone() {
        dao.replaceBlueprints(CORPORATION, List.of(
                new BlueprintEntry(11, 1_000, JITA, "CorpSAG1", -1, 10, 20, -1)));

        dao.replaceBlueprints(CORPORATION, List.of());

        assertEquals(List.of(), dao.listBlueprintRows());
    }
}
