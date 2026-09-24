package com.evefarm.service;

import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.db.dao.CharacterDao;
import com.evefarm.db.dao.EncounterDao;
import com.evefarm.db.dao.ItemTypeDao;
import com.evefarm.db.dao.OfficerDao;
import com.evefarm.db.dao.WalletJournalDao;
import com.evefarm.esi.UniverseApi;
import com.evefarm.model.NpcType;
import com.evefarm.model.ParsedEncounter;
import com.evefarm.model.SpawnRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpawnListingTest {

    private static final long PILOT = 90000001L;
    private static final long ALT = 90000002L;

    private CharacterDao characters;
    private EncounterDao encounters;
    private OfficerService officers;

    @BeforeEach
    void setUp() {
        Database database = new Database(":memory:");
        MigrationRunner.run(database);
        characters = new CharacterDao(database);
        characters.upsert(PILOT, "Pilot", null, List.of(), "owner-1");
        characters.upsert(ALT, "Alt", null, List.of(), "owner-2");
        encounters = new EncounterDao(database);
        NpcCatalogService catalog = mock(NpcCatalogService.class);
        when(catalog.catalog()).thenReturn(NpcCatalog.of(List.of(
                new NpcType(1, "Pithi Arrogator", 10, "Asteroid Guristas Frigate"),
                new NpcType(2, "Pithi Destructor", 11, "Asteroid Guristas Destroyer"),
                new NpcType(3, "Estamel Tharchon", 12, "Asteroid Guristas Officer"),
                new NpcType(4, "Burner Hawk", 13, "Mission Generic Frigates"))));
        officers = new OfficerService(encounters, mock(OfficerDao.class), mock(WalletJournalDao.class),
                mock(ItemTypeDao.class), catalog, mock(PriceService.class), mock(UniverseApi.class));
    }

    @Test
    void eachFightIsOneSpawnWithItsTimeKindAndComposition() {
        encounters.replaceForLogFile(PILOT, "pilot.txt", List.of(
                fight("2026-09-24T06:13:10Z", "2026-09-24T06:14:19Z", "Sotrentaira",
                        npc("Pithi Arrogator", 3, 30_000), npc("Pithi Destructor", 1, 45_000)),
                fight("2026-09-24T06:24:14Z", "2026-09-24T06:26:45Z", "Hageken",
                        npc("Pithi Arrogator", 2, 20_000), npc("Estamel Tharchon", 1, 0))));

        List<SpawnRow> spawns = officers.listAllSpawns();

        assertEquals(2, spawns.size());
        SpawnRow latest = spawns.get(0);
        assertEquals(Instant.parse("2026-09-24T06:24:14Z"), latest.startedAt());
        assertEquals("Officer", latest.kind());
        assertEquals(3, latest.killed());
        assertEquals(151, latest.durationSeconds());
        assertEquals("2× Pithi Arrogator, 1× Estamel Tharchon", latest.composition());
        SpawnRow earlier = spawns.get(1);
        assertEquals("Belt rat", earlier.kind());
        assertEquals(75_000, earlier.bounty());
        assertEquals("3× Pithi Arrogator, 1× Pithi Destructor", earlier.composition());
    }

    @Test
    void missionFightsAndFightsWithoutKillsAreRecognized() {
        encounters.replaceForLogFile(PILOT, "pilot.txt", List.of(
                fight("2026-09-24T07:00:00Z", "2026-09-24T07:05:00Z", "Jita", npc("Burner Hawk", 1, 0)),
                fight("2026-09-24T08:00:00Z", "2026-09-24T08:00:30Z", "Jita", npc("Pithi Arrogator", 0, 0))));

        List<SpawnRow> spawns = officers.listAllSpawns();

        assertEquals(1, spawns.size());
        assertEquals("Missions", spawns.get(0).kind());
    }

    @Test
    void removedCharactersAreLeftOut() {
        encounters.replaceForLogFile(PILOT, "pilot.txt", List.of(
                fight("2026-09-24T06:00:00Z", "2026-09-24T06:01:00Z", "Jita", npc("Pithi Arrogator", 1, 0))));
        encounters.replaceForLogFile(ALT, "alt.txt", List.of(
                fight("2026-09-24T06:30:00Z", "2026-09-24T06:31:00Z", "Jita", npc("Pithi Arrogator", 1, 0))));

        characters.remove(ALT);

        assertEquals(List.of("Pilot"), officers.listAllSpawns().stream().map(SpawnRow::characterName).toList());
    }

    private static ParsedEncounter fight(String start, String end, String system, ParsedEncounter.Npc... npcs) {
        return new ParsedEncounter(Instant.parse(start), Instant.parse(end), system, List.of(npcs));
    }

    private static ParsedEncounter.Npc npc(String name, int kills, double bounty) {
        Instant at = Instant.parse("2026-09-24T06:00:00Z");
        return new ParsedEncounter.Npc(name, at, at, kills, bounty, kills > 0 ? at : null, 0, 0);
    }
}
