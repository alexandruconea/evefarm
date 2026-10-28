package com.evefarm.service;

import com.evefarm.db.dao.WalletJournalDao.MissionIncomeEntry;
import com.evefarm.model.LpGain;
import com.evefarm.model.NpcType;
import com.evefarm.service.MissionIncome.AgentTotals;
import com.evefarm.service.MissionIncome.Kind;
import com.evefarm.service.MissionIncome.Payment;
import com.evefarm.service.MissionIncome.Period;
import com.evefarm.service.MissionIncome.Report;
import com.evefarm.service.MissionIncome.Totals;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MissionIncomeTest {

    private static final ZoneId BUCHAREST = ZoneId.of("Europe/Bucharest");
    private static final long MALPAIS = 2124165849L;
    private static final long BARSET = 2124000001L;
    private static final String BURNER_AGENT = "Yasonar Anoh";
    private static final String LEVEL_5_AGENT = "Nojana Dyodo";
    private static final NpcCatalog CATALOG = NpcCatalog.of(List.of(
            new NpcType(34155, "Burner Jaguar", 818, "Mission Generic Frigates"),
            new NpcType(34189, "Burner Clone Soldier Transport", 790, "Asteroid Angel Cartel Hauler"),
            new NpcType(12256, "Zor", 816, "Mission Generic Battleships"),
            new NpcType(32960, "Matriarch Alvus", 1174, "Deadspace Rogue Drone Battleship"),
            new NpcType(11899, "Blood Seeker", 555, "Asteroid Blood Raiders Frigate")));

    private static MissionIncomeEntry entry(long characterId, String date, String refType, double amount,
                                            String reason, String agent) {
        return new MissionIncomeEntry(characterId, characterId == MALPAIS ? "Malpais Legate" : "Barset",
                Instant.parse(date), refType, amount, reason, agent);
    }

    private static MissionIncomeEntry reward(String date, double amount, String agent) {
        return entry(MALPAIS, date, MissionIncome.REWARD, amount, "", agent);
    }

    private static MissionIncomeEntry bonus(String date, double amount, String agent) {
        return entry(MALPAIS, date, MissionIncome.TIME_BONUS, amount, "", agent);
    }

    private static MissionIncomeEntry bounty(long characterId, String date, double amount, String reason) {
        return entry(characterId, date, "bounty_prizes", amount, reason, null);
    }

    private static List<Payment> classify(MissionIncomeEntry... entries) {
        return MissionIncome.classify(List.of(entries), CATALOG::isMissionNpc);
    }

    @Test
    void burnerBountiesCountEvenWithoutATurnInNearby() {
        List<Payment> payments = classify(
                bounty(MALPAIS, "2026-10-28T19:58:52Z", 4_950_000, "34155: 1"),
                bounty(MALPAIS, "2026-10-28T08:51:23Z", 26_700_000, "34189: 1"));

        assertEquals(List.of(Kind.MISSION_NPCS, Kind.MISSION_NPCS), payments.stream().map(Payment::kind).toList(),
                "the burners' transport is filed under an asteroid group, but it is a burner too");
        assertNull(payments.get(0).agentName());
    }

    @Test
    void otherNpcsCountWhenTheyArePaidCloseToATurnIn() {
        List<Payment> payments = classify(
                reward("2026-10-12T18:10:45Z", 4_000_000, LEVEL_5_AGENT),
                bounty(MALPAIS, "2026-10-12T18:11:45Z", 21_900_000, "32960: 4"),
                bounty(MALPAIS, "2026-10-12T17:15:00Z", 5_000_000, "32960: 2"),
                bounty(MALPAIS, "2026-10-12T19:31:18Z", 5_500_000, "32960: 3"),
                bounty(MALPAIS, "2026-10-12T16:10:00Z", 4_600_000, "32960: 1"),
                bounty(BARSET, "2026-10-12T18:11:45Z", 900_000, "11899: 2"));

        assertEquals(List.of(Kind.REWARD, Kind.NEAR_TURN_IN, Kind.NEAR_TURN_IN, Kind.OTHER_BOUNTY,
                        Kind.OTHER_BOUNTY, Kind.OTHER_BOUNTY), payments.stream().map(Payment::kind).toList(),
                "paid 1 minute after and 56 minutes before the turn-in count; 81 minutes after, two hours before "
                        + "or for another character don't");
        assertEquals(LEVEL_5_AGENT, payments.get(1).agentName());
        assertNull(payments.get(3).agentName());
    }

    @Test
    void aBountyGoesToTheAgentOfTheClosestTurnIn() {
        List<Payment> payments = classify(
                reward("2026-10-28T19:00:00Z", 1_000_000, BURNER_AGENT),
                reward("2026-10-28T19:30:00Z", 1_000_000, LEVEL_5_AGENT),
                bounty(MALPAIS, "2026-10-28T19:10:00Z", 4_950_000, "34155: 1"),
                bounty(MALPAIS, "2026-10-28T19:20:00Z", 4_950_000, "34155: 1"));

        assertEquals(BURNER_AGENT, payments.get(2).agentName());
        assertEquals(LEVEL_5_AGENT, payments.get(3).agentName());
    }

    @Test
    void aMissionThatPaysOnlyATimeBonusStillCountsAsATurnIn() {
        List<Payment> payments = classify(
                bonus("2026-10-13T18:58:05Z", 314_280, "Teikoras Uehiro"),
                bounty(MALPAIS, "2026-10-13T19:10:00Z", 9_700_000, "32960: 2"),
                reward("2026-10-28T20:55:10Z", 1_940_400, BURNER_AGENT),
                bonus("2026-10-28T20:55:10Z", 1_801_800, BURNER_AGENT));

        assertEquals(List.of(true, false, true, false), payments.stream().map(Payment::turnIn).toList());
        assertEquals(Kind.NEAR_TURN_IN, payments.get(1).kind());
        Report report = MissionIncome.report(payments, List.of(), LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31), BUCHAREST);
        assertEquals(2, report.totals().missions());
        assertEquals(List.of(1, 1), report.agents().stream().map(AgentTotals::missions).toList());
    }

    @Test
    void thePeriodsFollowTheCalendar() {
        LocalDate wednesday = LocalDate.of(2026, 10, 28);

        assertEquals(wednesday, Period.DAY.start(wednesday, DayOfWeek.MONDAY));
        assertEquals(LocalDate.of(2026, 10, 26), Period.WEEK.start(wednesday, DayOfWeek.MONDAY));
        assertEquals(LocalDate.of(2026, 10, 25), Period.WEEK.start(wednesday, DayOfWeek.SUNDAY));
        assertEquals(LocalDate.of(2026, 11, 1), Period.WEEK.end(LocalDate.of(2026, 10, 26)));
        assertEquals(LocalDate.of(2026, 10, 1), Period.MONTH.start(wednesday, DayOfWeek.MONDAY));
        assertEquals(LocalDate.of(2026, 2, 28), Period.MONTH.end(LocalDate.of(2026, 2, 1)));
        assertEquals(LocalDate.of(2026, 9, 1), Period.MONTH.shift(LocalDate.of(2026, 10, 1), -1));
        assertEquals(LocalDate.of(2026, 10, 19), Period.WEEK.shift(LocalDate.of(2026, 10, 26), -1));
    }

    @Test
    void theReportAddsUpTheChosenDaysInLocalTime() {
        List<Payment> payments = classify(
                reward("2026-10-27T22:30:00Z", 1_940_400, BURNER_AGENT),
                bonus("2026-10-27T22:30:00Z", 1_801_800, BURNER_AGENT),
                bounty(MALPAIS, "2026-10-27T22:40:00Z", 4_950_000, "34155: 1"),
                reward("2026-10-28T10:00:00Z", 4_000_000, LEVEL_5_AGENT),
                bounty(MALPAIS, "2026-10-28T10:05:00Z", 14_000_000, "32960: 5"),
                bounty(MALPAIS, "2026-10-28T15:00:00Z", 495_000, "12256: 1"),
                bounty(BARSET, "2026-10-28T13:00:00Z", 900_000, "11899: 2"),
                bounty(BARSET, "2026-10-29T06:58:39Z", 0, "11899: 1"),
                reward("2026-10-27T20:00:00Z", 9_000_000, BURNER_AGENT));
        List<LpGain> gains = List.of(
                new LpGain(MALPAIS, "Malpais Legate", "Carthum Conglomerate", 6_000,
                        Instant.parse("2026-10-28T11:00:00Z"), Instant.parse("2026-10-28T10:00:00Z")),
                new LpGain(MALPAIS, "Malpais Legate", "Paragon", 2_000,
                        Instant.parse("2026-10-28T12:00:00Z"), Instant.parse("2026-10-27T18:00:00Z")),
                new LpGain(MALPAIS, "Malpais Legate", "Carthum Conglomerate", 9_000,
                        Instant.parse("2026-10-27T20:00:00Z"), null));
        LocalDate day = LocalDate.of(2026, 10, 28);

        Report report = MissionIncome.report(payments, gains, day, day, BUCHAREST);
        Report nextDay = MissionIncome.report(payments, gains, day.plusDays(1), day.plusDays(1), BUCHAREST);

        assertEquals(new Totals(2, 5_940_400, 1_801_800, 5_445_000, 14_000_000, 900_000, 8_000), report.totals(),
                "22:30 UTC on Oct 27 is already Oct 28 in Bucharest; 20:00 UTC isn't");
        assertEquals(27_187_200, report.totals().isk(), 0.001);
        assertEquals(1, report.days().size());
        assertEquals(List.of("Carthum Conglomerate", "Paragon"), report.corporations().stream()
                .map(MissionIncome.CorporationLp::corporationName).toList());
        assertEquals(Instant.parse("2026-10-27T18:00:00Z"), report.lpEarnedSince());
        assertEquals(List.of(new AgentTotals(LEVEL_5_AGENT, 1, 4_000_000, 0, 14_000_000),
                        new AgentTotals(BURNER_AGENT, 1, 1_940_400, 1_801_800, 4_950_000),
                        new AgentTotals(null, 0, 0, 0, 495_000)), report.agents(),
                "the ratting bounty isn't listed under any agent");
        assertEquals(8_692_200, report.agents().get(1).perMission(), 0.001);
        assertEquals(List.of(), nextDay.days(), "a bounty of 0 ISK leaves no empty day");
    }
}
