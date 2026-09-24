package com.evefarm.service;

import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.db.dao.CharacterDao;
import com.evefarm.db.dao.KillDao;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.model.NpcType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BeltKillMilestoneServiceTest {

    private static final long PILOT = 90000001L;
    private static final long ALT = 90000002L;

    private Database database;
    private SettingsDao settings;
    private CharacterDao characters;
    private BeltKillMilestoneService milestones;
    private int killSequence;

    @BeforeEach
    void setUp() {
        database = new Database(":memory:");
        MigrationRunner.run(database);
        settings = new SettingsDao(database);
        characters = new CharacterDao(database);
        characters.upsert(PILOT, "Pilot", null, List.of(), "owner-1");
        characters.upsert(ALT, "Alt", null, List.of(), "owner-2");
        NpcCatalogService catalogService = mock(NpcCatalogService.class);
        when(catalogService.catalog()).thenReturn(NpcCatalog.of(List.of(
                new NpcType(1, "Pithi Arrogator", 10, "Asteroid Guristas Frigate"),
                new NpcType(2, "Estamel Tharchon", 11, "Asteroid Guristas Officer"),
                new NpcType(3, "Burner Hawk", 12, "Mission Generic Frigates"))));
        milestones = new BeltKillMilestoneService(new KillDao(database), settings, catalogService);
    }

    @Test
    void theFirstCheckOnlyRemembersWhereYouAre() throws SQLException {
        kill(PILOT, "Pithi Arrogator", 1500);

        assertEquals(OptionalLong.empty(), milestones.checkForNewMilestone());
        assertEquals("1000", settings.get(SettingsDao.BELT_KILL_MILESTONE).orElseThrow());
    }

    @Test
    void eachNewThousandIsAnnouncedOnce() throws SQLException {
        kill(PILOT, "Pithi Arrogator", 1500);
        milestones.checkForNewMilestone();

        kill(ALT, "Pithi Arrogator", 799);
        kill(PILOT, "Estamel Tharchon", 1);

        assertEquals(OptionalLong.of(2000), milestones.checkForNewMilestone());
        assertEquals(OptionalLong.empty(), milestones.checkForNewMilestone());
    }

    @Test
    void onlyNpcsFromAsteroidBeltsCount() throws SQLException {
        kill(PILOT, "Pithi Arrogator", 10);
        kill(PILOT, "Estamel Tharchon", 1);
        kill(PILOT, "Burner Hawk", 5000);
        kill(PILOT, "Unknown Drone", 3000);

        assertEquals(11, milestones.countBeltKills());
    }

    @Test
    void removedCharactersNoLongerCount() throws SQLException {
        kill(PILOT, "Pithi Arrogator", 10);
        kill(ALT, "Pithi Arrogator", 20);

        characters.remove(ALT);

        assertEquals(10, milestones.countBeltKills());
    }

    private void kill(long characterId, String npcName, int count) throws SQLException {
        String sql = "INSERT INTO character_kills(character_id, killed_at, npc_name, faction_label) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = database.connection().prepareStatement(sql)) {
            for (int i = 0; i < count; i++) {
                ps.setLong(1, characterId);
                ps.setString(2, "2026-09-24T10:00:00." + String.format("%06d", killSequence++) + "Z");
                ps.setString(3, npcName);
                ps.setString(4, "Guristas Pirates");
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
}
