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
import static org.junit.jupiter.api.Assertions.assertNull;

class WalletJournalDaoTest {

    private static final long CHARACTER_ID = 2124165849L;

    private Database database;
    private WalletJournalDao dao;

    @BeforeEach
    void setUp() {
        database = new Database(":memory:");
        MigrationRunner.run(database);
        new CharacterDao(database).upsert(CHARACTER_ID, "Malpais Legate", List.of(), null);
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

    @Test
    void missionIncomeHasTheRewardsAndBountiesWithTheAgentsName() {
        new EntityNameCacheDao(database).upsert(3014399L, "Yasonar Anoh", "character");
        dao.saveForCharacter(CHARACTER_ID, List.of(
                new JournalEntry(1, "2026-10-28T20:55:10Z", "agent_mission_reward", 1_940_400, 0,
                        "Mission reward from agent Yasonar Anoh to Malpais Legate", "", 3014399, (int) CHARACTER_ID,
                        19_600, null),
                new JournalEntry(2, "2026-10-28T20:55:10Z", "agent_mission_time_bonus_reward", 1_801_800, 0,
                        "Mission reward bonus from agent Yasonar Anoh to Malpais Legate", "", 3014399,
                        (int) CHARACTER_ID, 18_200, null),
                payout(3, "2026-10-28T20:43:54Z", 495_000),
                new JournalEntry(4, "2026-10-28T12:29:49Z", "daily_goal_payouts", 1_000_000, 0, "", "", null,
                        (int) CHARACTER_ID, 0, null)));

        List<WalletJournalDao.MissionIncomeEntry> income = dao.findMissionIncome();

        assertEquals(List.of("bounty_prizes", "agent_mission_reward", "agent_mission_time_bonus_reward"),
                income.stream().map(WalletJournalDao.MissionIncomeEntry::refType).toList());
        assertEquals("Yasonar Anoh", income.get(1).agentName());
        assertNull(income.get(0).agentName(), "a bounty is paid by CONCORD, not an agent");
        assertEquals("16037: 1", income.get(0).reason());
    }
}
