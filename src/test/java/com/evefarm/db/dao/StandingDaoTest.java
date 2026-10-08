package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.model.StandingEntry;
import com.evefarm.model.StandingRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StandingDaoTest {

    private static final long PILOT = 90_000_001L;
    private static final long CALDARI_STATE = 500001L;
    private static final long CALDARI_NAVY = 1000035L;
    private static final long AGENT = 3008416L;

    private Database database;
    private CharacterDao characters;
    private StandingDao standings;

    @BeforeEach
    void setUp() throws SQLException {
        database = new Database(":memory:");
        MigrationRunner.run(database);
        characters = new CharacterDao(database);
        characters.upsert(PILOT, "Pilot", List.of(), "owner");
        standings = new StandingDao(database);
        try (Statement statement = database.connection().createStatement()) {
            statement.execute("INSERT INTO entity_name_cache(entity_id, name, category, cached_at) VALUES "
                    + "(500001, 'Caldari State', 'faction', 'now'), (1000035, 'Caldari Navy', 'corporation', 'now')");
            statement.execute("INSERT INTO sde_agent(agent_id, agent_name, corporation_id, corporation_name, "
                    + "faction_id, faction_name, division_name, agent_type_name, level, is_locator, "
                    + "solar_system_name) VALUES (3008416, 'Antaken Kamola', 1000035, 'Caldari Navy', 500001, "
                    + "'Caldari State', 'Security', 'BasicAgent', 4, 0, 'Josameto')");
        }
    }

    @Test
    void factionsComeFirstThenCorporationsThenAgentsEachBestFirst() {
        standings.replaceForCharacter(PILOT, List.of(
                new StandingEntry(AGENT, "agent", 2.5),
                new StandingEntry(CALDARI_NAVY, "npc_corp", 4.1),
                new StandingEntry(CALDARI_STATE, "faction", -1.25)));

        List<StandingRow> rows = standings.listRows();

        assertEquals(List.of("Caldari State", "Caldari Navy", "Antaken Kamola"),
                rows.stream().map(StandingRow::name).toList());
        assertEquals(-1.25, rows.getFirst().standing(), 1e-9);
    }

    @Test
    void agentsAndCorporationsAreDescribedFromTheAgentCatalog() {
        standings.replaceForCharacter(PILOT, List.of(
                new StandingEntry(AGENT, "agent", 2.5),
                new StandingEntry(CALDARI_NAVY, "npc_corp", 4.1),
                new StandingEntry(CALDARI_STATE, "faction", 3.0)));

        List<StandingRow> rows = standings.listRows();
        StandingRow faction = rows.get(0);
        StandingRow corporation = rows.get(1);
        StandingRow agent = rows.get(2);

        assertNull(faction.factionName());
        assertEquals("Caldari State", corporation.factionName());
        assertNull(corporation.level());
        assertEquals("Caldari Navy", agent.corporationName());
        assertEquals("Caldari State", agent.factionName());
        assertEquals(4, agent.level());
        assertEquals("Security", agent.divisionName());
        assertEquals("Josameto", agent.systemName());
    }

    @Test
    void anUnknownNameShowsTheId() {
        standings.replaceForCharacter(PILOT, List.of(new StandingEntry(1000999L, "npc_corp", 0.5)));

        assertEquals("#1000999", standings.listRows().getFirst().name());
    }

    @Test
    void anUpdateReplacesTheCharactersStandings() {
        standings.replaceForCharacter(PILOT, List.of(
                new StandingEntry(CALDARI_NAVY, "npc_corp", 4.1),
                new StandingEntry(AGENT, "agent", 2.5)));
        standings.replaceForCharacter(PILOT, List.of(new StandingEntry(CALDARI_NAVY, "npc_corp", 4.3)));

        List<StandingRow> rows = standings.listRows();

        assertEquals(1, rows.size());
        assertEquals(4.3, rows.getFirst().standing(), 1e-9);
    }

    @Test
    void removingTheCharacterRemovesItsStandings() {
        standings.replaceForCharacter(PILOT, List.of(new StandingEntry(CALDARI_NAVY, "npc_corp", 4.1)));

        characters.remove(PILOT);
        assertEquals(List.of(), standings.listRows());

        characters.upsert(PILOT, "Pilot", List.of(), "owner");
        assertEquals(List.of(), standings.listRows());
    }
}
