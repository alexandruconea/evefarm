package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.model.AbyssFleet;
import com.evefarm.model.AbyssTier;
import com.evefarm.model.AbyssWeather;
import com.evefarm.model.AbyssalCargo;
import com.evefarm.model.AbyssalLoot;
import com.evefarm.model.AbyssalRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AbyssalRunDaoTest {

    private static final long PILOT = 90_000_001L;
    private static final long ALT = 90_000_002L;

    private Database database;
    private CharacterDao characters;
    private AbyssalRunDao runs;

    @BeforeEach
    void setUp() {
        database = new Database(":memory:");
        MigrationRunner.run(database);
        characters = new CharacterDao(database);
        characters.upsert(PILOT, "Abyss Runner", List.of(), "owner-1");
        characters.upsert(ALT, "Alt Runner", List.of(), "owner-2");
        runs = new AbyssalRunDao(database);
    }

    @Test
    void aRunIsSavedWithItsLootAndListedNewestFirst() {
        AbyssalRun older = run(PILOT, "2026-09-28T18:00:00Z", AbyssTier.FIERCE, AbyssWeather.EXOTIC, 12_000_000);
        AbyssalRun newer = run(ALT, "2026-09-28T19:00:00Z", null, null, 0);
        long olderId = runs.save(older, List.of(
                new AbyssalLoot(34, "Tritanium", 2_500, 4.0),
                new AbyssalLoot(48121, "Triglavian Survey Database", 12, 100_000.0),
                new AbyssalLoot(47408, "Unstable Damage Control Mutaplasmid", 1, null)));
        long newerId = runs.save(newer, List.of());

        assertEquals(List.of(newer.withId(newerId), older.withId(olderId)), runs.listRuns());
        assertEquals(List.of("Triglavian Survey Database", "Tritanium", "Unstable Damage Control Mutaplasmid"),
                runs.listLoot(olderId).stream().map(AbyssalLoot::typeName).toList());
    }

    @Test
    void savingAnExistingRunReplacesItsValuesAndLoot() {
        AbyssalRun run = run(PILOT, "2026-09-28T18:00:00Z", AbyssTier.FIERCE, AbyssWeather.EXOTIC, 0);
        long id = runs.save(run, List.of(new AbyssalLoot(34, "Tritanium", 10, 4.0)));

        AbyssalRun edited = new AbyssalRun(id, PILOT, "Abyss Runner", run.startedAt(), 700, AbyssTier.RAGING,
                AbyssWeather.GAMMA, AbyssFleet.FRIGATES, null, "Gila", false, 30_000_000, 20_000_000.0,
                "lost to the Leshak");
        assertEquals(id, runs.save(edited, List.of(new AbyssalLoot(48121, "Triglavian Survey Database", 3, 100_000.0))));

        assertEquals(List.of(edited), runs.listRuns());
        assertEquals(List.of(new AbyssalLoot(48121, "Triglavian Survey Database", 3, 100_000.0)), runs.listLoot(id));
    }

    @Test
    void deletingARunDeletesItsLoot() throws SQLException {
        long id = runs.save(run(PILOT, "2026-09-28T18:00:00Z", null, null, 0),
                List.of(new AbyssalLoot(34, "Tritanium", 10, 4.0)));

        runs.delete(id);

        assertEquals(List.of(), runs.listRuns());
        try (Statement statement = database.connection().createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM abyssal_run_loot")) {
            rs.next();
            assertEquals(0, rs.getInt(1));
        }
    }

    @Test
    void theCargoARunWasCountedFromIsKeptUntilNewCargoIsSaved() throws SQLException {
        AbyssalRun run = run(PILOT, "2026-09-28T18:00:00Z", null, null, 0);
        long id = runs.save(run, List.of(), new AbyssalCargo("Tritanium\t10", "Tritanium\t40"));
        assertEquals(Optional.of(new AbyssalCargo("Tritanium\t10", "Tritanium\t40")), runs.findCargo(id));

        runs.save(run.withId(id).withLootValue(120), List.of());
        assertEquals(Optional.of(new AbyssalCargo("Tritanium\t10", "Tritanium\t40")), runs.findCargo(id));

        runs.save(run.withId(id), List.of(), new AbyssalCargo("", "Tritanium\t50"));
        assertEquals(Optional.of(new AbyssalCargo("", "Tritanium\t50")), runs.findCargo(id));

        runs.delete(id);
        assertEquals(Optional.empty(), runs.findCargo(id));
        try (Statement statement = database.connection().createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM abyssal_run_cargo")) {
            rs.next();
            assertEquals(0, rs.getInt(1));
        }
    }

    @Test
    void itemsCanBeIgnoredAsLootAndListedByName() {
        runs.ignoreItem(47928, "Imperial Navy Xray S");
        runs.ignoreItem(215, "Antimatter Charge S");
        runs.ignoreItem(47928, "Imperial Navy Xray S");

        assertEquals(Map.of(215, "Antimatter Charge S", 47928, "Imperial Navy Xray S"), runs.listIgnoredItems());
        assertEquals(List.of("Antimatter Charge S", "Imperial Navy Xray S"),
                List.copyOf(runs.listIgnoredItems().values()));

        runs.unignoreItem(215);
        assertEquals(Map.of(47928, "Imperial Navy Xray S"), runs.listIgnoredItems());
    }

    @Test
    void runsOfARemovedCharacterAreHiddenButKept() {
        runs.save(run(ALT, "2026-09-28T18:00:00Z", null, null, 0), List.of());

        characters.remove(ALT);
        assertEquals(List.of(), runs.listRuns());

        characters.upsert(ALT, "Alt Runner", List.of(), "owner-2");
        assertEquals(1, runs.listRuns().size());
    }

    private static AbyssalRun run(long characterId, String startedAt, AbyssTier tier, AbyssWeather weather,
                                  double loot) {
        return new AbyssalRun(0, characterId, characterId == PILOT ? "Abyss Runner" : "Alt Runner",
                Instant.parse(startedAt), 900, tier, weather, AbyssFleet.CRUISER, 17715, "Gila", true, loot,
                9_500_000.0, null);
    }
}
