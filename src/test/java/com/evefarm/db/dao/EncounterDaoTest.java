package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.model.ItemType;
import com.evefarm.model.OfficerDrop;
import com.evefarm.model.OfficerSighting;
import com.evefarm.model.ParsedEncounter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EncounterDaoTest {

    private static final long CHARACTER_ID = 2124165849L;
    private static final Instant FIRST_SEEN = Instant.parse("2026-10-02T21:11:20Z");

    private EncounterDao encounterDao;
    private OfficerDao officerDao;

    @BeforeEach
    void setUp() {
        Database database = new Database(":memory:");
        MigrationRunner.run(database);
        new CharacterDao(database).upsert(CHARACTER_ID, "Malpais Legate", null, List.of(), null);
        encounterDao = new EncounterDao(database);
        officerDao = new OfficerDao(database);
    }

    private static ParsedEncounter officerFight() {
        return new ParsedEncounter(Instant.parse("2026-10-02T21:11:02Z"), Instant.parse("2026-10-02T21:14:37Z"),
                "TXW-EI", List.of(
                new ParsedEncounter.Npc("Pithi Despoiler", Instant.parse("2026-10-02T21:11:02Z"),
                        Instant.parse("2026-10-02T21:12:01Z"), 2, 60_000, Instant.parse("2026-10-02T21:12:01Z"), 1960, 120),
                new ParsedEncounter.Npc("Dire Pithi Arrogator", Instant.parse("2026-10-02T21:11:10Z"),
                        Instant.parse("2026-10-02T21:13:00Z"), 1, 90_000, Instant.parse("2026-10-02T21:13:00Z"), 5000, 0),
                new ParsedEncounter.Npc("Estamel Tharchon", FIRST_SEEN,
                        Instant.parse("2026-10-02T21:14:37Z"), 1, 12_500_000, Instant.parse("2026-10-02T21:14:37Z"), 2400, 350)));
    }

    @Test
    void aSightingSumsTheRestOfItsFightAsEscort() {
        encounterDao.replaceForLogFile(CHARACTER_ID, "20261002_205000_2124165849.txt", List.of(officerFight()));

        List<OfficerSighting> sightings = encounterDao.listOfficerSightings(Set.of("Estamel Tharchon"));

        assertEquals(1, sightings.size());
        OfficerSighting sighting = sightings.get(0);
        assertEquals("Malpais Legate", sighting.characterName());
        assertEquals(FIRST_SEEN, sighting.firstSeenAt());
        assertEquals(Instant.parse("2026-10-02T21:14:37Z"), sighting.killedAt());
        assertEquals(12_500_000d, sighting.officerBounty());
        assertEquals(3, sighting.escortKills());
        assertEquals(2, sighting.escortTypes());
        assertEquals(3, encounterDao.listEncounterNpcs(sighting.encounterId()).size());
    }

    @Test
    void dropsAddUpOnTheSightingAndSurviveARescan() {
        String logFile = "20261002_205000_2124165849.txt";
        encounterDao.replaceForLogFile(CHARACTER_ID, logFile, List.of(officerFight()));
        officerDao.addDrop(CHARACTER_ID, "Estamel Tharchon", FIRST_SEEN,
                new ItemType(1, "Estamel's Modified Large Shield Booster", true), 1, 1_200_000_000);
        officerDao.addDrop(CHARACTER_ID, "Estamel Tharchon", FIRST_SEEN,
                new ItemType(2, "Estamel's Modified Shield Boost Amplifier", true), 2, 150_000_000);

        encounterDao.replaceForLogFile(CHARACTER_ID, logFile, List.of(officerFight()));

        OfficerSighting sighting = encounterDao.listOfficerSightings(Set.of("Estamel Tharchon")).get(0);
        assertEquals(1_500_000_000d, sighting.dropValue());
        assertEquals(1_512_500_000d, sighting.totalValue(), "drops plus the officer's own 12.5M bounty");

        List<OfficerDrop> drops = officerDao.listDrops(CHARACTER_ID, "Estamel Tharchon", FIRST_SEEN);
        assertEquals(2, drops.size());
        officerDao.removeDrop(drops.get(0).id());
        assertEquals(300_000_000d,
                encounterDao.listOfficerSightings(Set.of("Estamel Tharchon")).get(0).dropValue());
    }

    @Test
    void userDetailsSurviveTheLogFileBeingRescanned() {
        String logFile = "20261002_205000_2124165849.txt";
        encounterDao.replaceForLogFile(CHARACTER_ID, logFile, List.of(officerFight()));
        officerDao.saveDetails(CHARACTER_ID, "Estamel Tharchon", FIRST_SEEN, "TXW-EI VII - Asteroid Belt 3", "two neuts in local");

        encounterDao.replaceForLogFile(CHARACTER_ID, logFile, List.of(officerFight()));

        List<OfficerSighting> sightings = encounterDao.listOfficerSightings(Set.of("Estamel Tharchon"));
        assertEquals(1, sightings.size(), "the rescan replaced the fight rather than adding a second copy");
        assertEquals("TXW-EI VII - Asteroid Belt 3", sightings.get(0).belt());
        assertEquals("two neuts in local", sightings.get(0).notes());
        assertNull(sightings.get(0).payout());
    }
}
