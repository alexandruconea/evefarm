package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.model.JournalEntry;
import com.evefarm.model.ParsedEncounter;
import com.evefarm.model.TransactionEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CharacterDaoTest {

    private static final long CHARACTER_ID = 2124165849L;
    private static final Set<String> OFFICERS = Set.of("Estamel Tharchon");

    private Database database;
    private CharacterDao characters;
    private WalletJournalDao journal;
    private WalletTransactionDao transactions;
    private EncounterDao encounters;

    @BeforeEach
    void setUp() throws SQLException {
        database = new Database(":memory:");
        MigrationRunner.run(database);
        characters = new CharacterDao(database);
        journal = new WalletJournalDao(database);
        transactions = new WalletTransactionDao(database);
        encounters = new EncounterDao(database);

        characters.upsert(CHARACTER_ID, "Malpais Legate", null, List.of(), "first-owner");
        journal.saveForCharacter(CHARACTER_ID, List.of(new JournalEntry(1, "2026-09-23T15:20:00Z", "bounty_prizes",
                17_820_000, 0, "bounty prizes in TXW-EI", "13603: 1", null, null, 0, null)));
        transactions.saveForCharacter(CHARACTER_ID, List.of(new TransactionEntry(10, "2026-09-23T16:00:00Z", 34,
                100, 5.5, null, 60003760L, false, true, 99)));
        encounters.replaceForLogFile(CHARACTER_ID, "20260923_150500_2124165849.txt", List.of(new ParsedEncounter(
                Instant.parse("2026-09-23T15:10:00Z"), Instant.parse("2026-09-23T15:18:05Z"), "TXW-EI",
                List.of(new ParsedEncounter.Npc("Estamel Tharchon", Instant.parse("2026-09-23T15:15:50Z"),
                        Instant.parse("2026-09-23T15:18:05Z"), 1, 12_500_000, Instant.parse("2026-09-23T15:18:05Z"),
                        10_368, 1_872)))));
        execute("INSERT INTO tokens(character_id, refresh_token, updated_at) VALUES (" + CHARACTER_ID + ", 'x', 'now')");
        execute("INSERT INTO character_loyalty_points(character_id, corporation_id, loyalty_points, fetched_at) "
                + "VALUES (" + CHARACTER_ID + ", 1000125, 5000, 'now')");
    }

    @Test
    void removingHidesTheCharacterButKeepsItsHistory() throws SQLException {
        characters.remove(CHARACTER_ID);

        assertEquals(List.of(), characters.listAll());
        assertEquals(0, count("tokens"), "the login token goes");
        assertEquals(0, count("character_loyalty_points"), "current-state data goes");
        assertEquals(1, count("wallet_journal_entry"), "journal history is kept");
        assertEquals(1, count("wallet_transaction"), "trading history is kept");
        assertEquals(1, count("combat_encounter"), "officer fights are kept");

        assertEquals(List.of(), journal.listRows(null), "but none of it shows while removed");
        assertEquals(List.of(), transactions.listRows(null));
        assertEquals(List.of(), encounters.listOfficerSightings(OFFICERS));
        assertEquals(Optional.empty(), characters.findOwnerHash(CHARACTER_ID),
                "a removed character may come back under a new owner without tripping the transfer check");
    }

    @Test
    void addingTheCharacterAgainBringsItsHistoryBack() {
        characters.remove(CHARACTER_ID);
        characters.upsert(CHARACTER_ID, "Malpais Legate", null, List.of(), "new-owner");

        assertEquals(1, characters.listAll().size());
        assertEquals(1, journal.listRows(null).size());
        assertEquals(1, transactions.listRows(null).size());
        assertEquals(1, encounters.listOfficerSightings(OFFICERS).size());
        assertEquals(Optional.of("new-owner"), characters.findOwnerHash(CHARACTER_ID));
    }

    @Test
    void transactionsThatAgedOutOfEsiAreKept() {
        transactions.saveForCharacter(CHARACTER_ID, List.of(new TransactionEntry(11, "2026-10-30T12:00:00Z", 35,
                10, 7.0, null, 60003760L, true, true, 100)));

        assertEquals(2, transactions.listRows(null).size());
    }

    private void execute(String sql) throws SQLException {
        try (Statement statement = database.connection().createStatement()) {
            statement.execute(sql);
        }
    }

    private int count(String table) throws SQLException {
        try (Statement statement = database.connection().createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
