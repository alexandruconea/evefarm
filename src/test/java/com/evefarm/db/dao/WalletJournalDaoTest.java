package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.model.JournalEntry;
import com.evefarm.model.JournalPayout;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WalletJournalDaoTest {

    private static final long CHARACTER_ID = 2124165849L;

    private WalletJournalDao dao;

    @BeforeEach
    void setUp() {
        Database database = new Database(":memory:");
        MigrationRunner.run(database);
        new CharacterDao(database).upsert(CHARACTER_ID, "Malpais Legate", null, List.of(), null);
        dao = new WalletJournalDao(database);
    }

    private static JournalEntry payout(long id, String date, double amount) {
        return new JournalEntry(id, date, "bounty_prizes", amount, 0, "got bounty prizes for killing pirates in Sosh",
                "16037: 1", null, null, 0, null);
    }

    @Test
    void anUpdateKeepsEntriesThatHaveAgedOutOfEsi() {
        dao.saveForCharacter(CHARACTER_ID, List.of(payout(1, "2026-08-01T10:00:00Z", 970_000),
                payout(2, "2026-08-20T10:00:00Z", 970_000)));
        dao.saveForCharacter(CHARACTER_ID, List.of(payout(2, "2026-08-20T10:00:00Z", 970_000),
                payout(3, "2026-09-10T10:00:00Z", 4_850_000)));

        List<JournalPayout> all = dao.findBountyPayouts(CHARACTER_ID,
                Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-10-01T00:00:00Z"));
        assertEquals(List.of(Instant.parse("2026-08-01T10:00:00Z"), Instant.parse("2026-08-20T10:00:00Z"),
                Instant.parse("2026-09-10T10:00:00Z")), all.stream().map(JournalPayout::paidAt).toList(),
                "entry 2 was saved twice but must be stored once");
    }
}
