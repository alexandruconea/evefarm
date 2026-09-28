package com.evefarm.service;

import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.db.dao.CharacterDao;
import com.evefarm.db.dao.EncounterDao;
import com.evefarm.db.dao.ItemTypeDao;
import com.evefarm.db.dao.OfficerDao;
import com.evefarm.db.dao.WalletJournalDao;
import com.evefarm.esi.UniverseApi;
import com.evefarm.model.ItemType;
import com.evefarm.model.NpcType;
import com.evefarm.model.OfficerDrop;
import com.evefarm.model.OfficerSighting;
import com.evefarm.model.ParsedEncounter;
import com.evefarm.model.SpawnMember;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OfficerTeamKillTest {

    private static final long BARSET = 90000001L;
    private static final long NOZEU = 90000002L;
    private static final long MALPAIS = 90000003L;
    private static final String OFFICER = "Estamel Tharchon";

    private EncounterDao encounters;
    private OfficerDao officerDao;
    private OfficerService officers;

    @BeforeEach
    void setUp() {
        Database database = new Database(":memory:");
        MigrationRunner.run(database);
        CharacterDao characters = new CharacterDao(database);
        characters.upsert(BARSET, "Barset", null, List.of(), "owner-1");
        characters.upsert(NOZEU, "Nozeu", null, List.of(), "owner-1");
        characters.upsert(MALPAIS, "Malpais Legate", null, List.of(), "owner-1");
        encounters = new EncounterDao(database);
        officerDao = new OfficerDao(database);
        NpcCatalogService catalog = mock(NpcCatalogService.class);
        when(catalog.catalog()).thenReturn(NpcCatalog.of(List.of(
                new NpcType(1, "Pithi Arrogator", 10, "Asteroid Guristas Frigate"),
                new NpcType(3, OFFICER, 12, "Asteroid Guristas Officer"))));
        officers = new OfficerService(encounters, officerDao, new WalletJournalDao(database),
                mock(ItemTypeDao.class), catalog, mock(PriceService.class), mock(UniverseApi.class));
    }

    @Test
    void anOfficerKilledByThreeCharactersIsOneKilledSighting() {
        fightFor(NOZEU, "21:11:05", "21:14:40", "21:11:20", null, 0, 38_100, 2);
        fightFor(BARSET, "21:11:02", "21:14:37", "21:11:22", "21:14:37", 12_500_000, 45_210, 1);
        fightFor(MALPAIS, "21:12:30", "21:14:39", "21:12:40", null, 0, 12_040, 0);

        List<OfficerSighting> sightings = officers.listSightings();

        assertEquals(1, sightings.size());
        OfficerSighting sighting = sightings.get(0);
        assertTrue(sighting.killed());
        assertEquals("Barset, Nozeu, Malpais Legate", sighting.characterName());
        assertEquals(BARSET, sighting.characterId());
        assertEquals(12_500_000.0, sighting.officerBounty());
        assertEquals(3, sighting.escortKills());
        assertEquals(at("21:11:20"), sighting.earliestSeenAt());
        assertEquals(at("21:11:02"), sighting.fightStartedAt());
        assertEquals(at("21:14:40"), sighting.fightEndedAt());
        assertEquals(3, sighting.encounterIds().size());
    }

    @Test
    void dropsAndDetailsSavedOnAnyOfTheCharactersBelongToTheSharedSighting() {
        fightFor(NOZEU, "21:11:05", "21:14:40", "21:11:20", null, 0, 38_100, 0);
        officerDao.addDrop(NOZEU, OFFICER, at("21:11:20"), new ItemType(1, "Estamel's Modified Shield Boost Amplifier",
                true), 1, 150_000_000);
        officerDao.saveDetails(NOZEU, OFFICER, at("21:11:20"), "Belt 3", null);
        fightFor(BARSET, "21:11:02", "21:14:37", "21:11:22", "21:14:37", 12_500_000, 45_210, 0);

        OfficerSighting sighting = officers.listSightings().get(0);
        assertEquals(150_000_000.0, sighting.dropValue());
        assertEquals("Belt 3", sighting.belt());

        officers.addDrop(sighting, new ItemType(2, "Estamel's Modified Large Shield Booster", true), 1, 1_200_000_000);
        officers.saveDetails(sighting, "Belt 3", "two neuts in local");

        OfficerSighting updated = officers.listSightings().get(0);
        assertEquals(List.of("Estamel's Modified Large Shield Booster", "Estamel's Modified Shield Boost Amplifier"),
                officers.listDrops(updated).stream().map(OfficerDrop::typeName).toList());
        assertEquals(1_350_000_000.0, updated.dropValue());
        assertEquals("two neuts in local", updated.notes());
        assertEquals(List.of("two neuts in local", "two neuts in local"),
                encounters.listOfficerSightings(Set.of(OFFICER)).stream().map(OfficerSighting::notes).toList(),
                "the notes are saved for every character, so no older note shows up instead");
    }

    @Test
    void theSpawnOfASharedSightingListsTheNpcsOfEveryCharacter() {
        fightFor(NOZEU, "21:11:05", "21:14:40", "21:11:20", null, 0, 38_100, 2);
        fightFor(BARSET, "21:11:02", "21:14:37", "21:11:22", "21:14:37", 12_500_000, 45_210, 1);

        OfficerSighting sighting = officers.listSightings().get(0);
        List<SpawnMember> spawn = officers.listSpawn(sighting.encounterIds());

        assertEquals(List.of(OFFICER, "Pithi Arrogator"), spawn.stream().map(member -> member.npc().name()).toList());
        assertEquals(3, spawn.get(1).npc().kills());
        assertEquals(1, spawn.get(0).npc().kills());
        assertEquals(83_310, spawn.get(0).npc().damageDealt());
    }

    @Test
    void theSameOfficerInAnotherSystemOrMuchLaterIsAnotherSighting() {
        fightFor(BARSET, "21:11:02", "21:14:37", "21:11:22", "21:14:37", 12_500_000, 45_210, 0);
        encounters.replaceForLogFile(NOZEU, "nozeu-other.txt", List.of(fight("21:12:00", "21:13:00", "Hibi",
                officer("21:12:10", null, 0, 5_000))));
        encounters.replaceForLogFile(MALPAIS, "malpais-later.txt", List.of(fight("21:40:00", "21:44:00", "TXW-EI",
                officer("21:40:10", null, 0, 9_000))));

        List<OfficerSighting> sightings = officers.listSightings();

        assertEquals(3, sightings.size());
        assertEquals(List.of("Malpais Legate", "Nozeu", "Barset"),
                sightings.stream().map(OfficerSighting::characterName).toList());
    }

    private void fightFor(long characterId, String start, String end, String officerSeen, String killedAt,
                          double bounty, long damage, int escortKills) {
        encounters.replaceForLogFile(characterId, characterId + ".txt", List.of(fight(start, end, "TXW-EI",
                officer(officerSeen, killedAt, bounty, damage),
                new ParsedEncounter.Npc("Pithi Arrogator", at(start), at(end), escortKills, escortKills * 30_000.0,
                        escortKills > 0 ? at(end) : null, 1_000, 0))));
    }

    private static ParsedEncounter fight(String start, String end, String system, ParsedEncounter.Npc... npcs) {
        return new ParsedEncounter(at(start), at(end), system, List.of(npcs));
    }

    private static ParsedEncounter.Npc officer(String seen, String killedAt, double bounty, long damage) {
        return new ParsedEncounter.Npc(OFFICER, at(seen), killedAt == null ? at(seen) : at(killedAt),
                killedAt == null ? 0 : 1, bounty, killedAt == null ? null : at(killedAt), damage, 0);
    }

    private static Instant at(String time) {
        return Instant.parse("2026-10-02T" + time + "Z");
    }
}
