package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.model.Accelerator;
import com.evefarm.model.CharacterAccelerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AcceleratorDaoTest {

    private static final long PILOT = 2124165849L;
    private static final Instant FIRST_SEEN = Instant.parse("2026-10-20T18:00:00Z");

    private AcceleratorDao dao;

    @BeforeEach
    void setUp() {
        Database database = new Database(":memory:");
        MigrationRunner.run(database);
        new CharacterDao(database).upsert(PILOT, "Malpais Legate", List.of(), null);
        dao = new AcceleratorDao(database);
    }

    @Test
    void aNewCatalogReplacesTheOldOneAndIsReadInTypeOrder() {
        dao.replaceCatalog(List.of(new Accelerator(36000, "Retired Accelerator", 2, 24)));
        Accelerator standard = new Accelerator(45635, "Standard Cerebral Accelerator", 3, 168);
        Accelerator expert = new Accelerator(40517, "Expert Cerebral Accelerator", 10, 720.5);

        dao.replaceCatalog(List.of(standard, expert));

        assertEquals(List.of(expert, standard), dao.catalog());
    }

    @Test
    void aCharactersAcceleratorIsSavedReplacedAndCleared() {
        assertEquals(Optional.empty(), dao.find(PILOT));
        CharacterAccelerator seen = new CharacterAccelerator(PILOT, 45635, "Standard Cerebral Accelerator", 3,
                FIRST_SEEN, null, false);

        dao.save(seen);
        assertEquals(Optional.of(seen), dao.find(PILOT));

        CharacterAccelerator ended = seen.withEnd(Instant.parse("2026-10-27T18:00:00Z"), true);
        dao.save(ended);
        assertEquals(Optional.of(ended), dao.find(PILOT));

        dao.delete(PILOT);
        assertEquals(Optional.empty(), dao.find(PILOT));
    }

    @Test
    void anAcceleratorWithoutAKnownTypeKeepsItsName() {
        CharacterAccelerator unknown = new CharacterAccelerator(PILOT, null, "Event Accelerator", 12, FIRST_SEEN,
                Instant.parse("2026-10-25T18:00:00Z"), true);

        dao.save(unknown);

        assertEquals(Optional.of(unknown), dao.find(PILOT));
    }
}
