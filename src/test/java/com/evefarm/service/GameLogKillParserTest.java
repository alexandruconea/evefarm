package com.evefarm.service;

import com.evefarm.model.ParsedEncounter;
import com.evefarm.model.ParsedKill;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameLogKillParserTest {

    @Test
    void extractsCharacterIdFromAThreeSegmentFileName() {
        assertEquals(Optional.of(99000123L),
                GameLogKillParser.extractCharacterId("20260922_050322_99000123.txt"));
    }

    @Test
    void twoSegmentPreLoginFileNameHasNoCharacterId() {
        assertEquals(Optional.empty(), GameLogKillParser.extractCharacterId("20260921_124724.txt"));
    }

    @Test
    void bountyLineAttributesTheKillToTheMostRecentlyDamagedTarget() {
        List<String> lines = List.of(
                "[ 2026.09.22 07:51:32 ] (combat) <color=0xff00ffff><b>417</b> <color=0x77ffffff><font size=10>to</font> <b><color=0xffffffff>Burner Hawk</b><font size=10><color=0x77ffffff> - Mjolnir Rage Rocket - Hits",
                "[ 2026.09.22 07:52:59 ] (combat) <color=0xff00ffff><b>211</b> <color=0x77ffffff><font size=10>to</font> <b><color=0xffffffff>Burner Hawk</b><font size=10><color=0x77ffffff> - Mjolnir Rage Rocket - Hits",
                "[ 2026.09.22 07:52:59 ] (bounty) <font size=12><b><color=0xff00aa00>5,000,000 ISK</b><color=0x77ffffff> added to next bounty payout",
                "[ 2026.09.22 07:53:00 ] (notify) Mjolnir Rage Rocket deactivates as the item it was targeted at is no longer present."
        );

        List<ParsedKill> kills = GameLogKillParser.parseKills(lines);

        assertEquals(1, kills.size());
        assertEquals("Burner Hawk", kills.get(0).npcName());
        assertEquals(Instant.parse("2026-09-22T07:52:59Z"), kills.get(0).killedAt());
    }

    @Test
    void namedMissionBossWithNoFactionWordInItsNameIsStillCaptured() {
        List<String> lines = List.of(
                "[ 2026.09.22 06:44:39 ] (combat) <color=0xff00ffff><b>2488</b> <color=0x77ffffff><font size=10>to</font> <b><color=0xffffffff>Mercenary Overlord</b><font size=10><color=0x77ffffff> - Neutron Blaster Cannon II - Grazes",
                "[ 2026.09.22 06:44:47 ] (combat) <color=0xff00ffff><b>4397</b> <color=0x77ffffff><font size=10>to</font> <b><color=0xffffffff>Mercenary Overlord</b><font size=10><color=0x77ffffff> - Neutron Blaster Cannon II - Hits",
                "[ 2026.09.22 06:44:48 ] (bounty) <font size=12><b><color=0xff00aa00>250,000 ISK</b><color=0x77ffffff> added to next bounty payout"
        );

        List<ParsedKill> kills = GameLogKillParser.parseKills(lines);

        assertEquals(1, kills.size());
        assertEquals("Mercenary Overlord", kills.get(0).npcName());
        assertEquals(GameLogKillParser.OTHER_FACTION, kills.get(0).factionLabel(),
                "a named/burner NPC with no recognized faction word must land in the Other bucket, not be misclassified");
    }

    @Test
    void aTargetMentionedInAMessyMultiDirectionLineIsNotConfusedWithTheWordYou() {
        List<String> lines = List.of(
                "[ 2026.09.22 05:10:12 ] (combat) <color=0xffffffff><b>Warp scramble attempt</b> <color=0x77ffffff><font size=10>from</font> <color=0xffffffff><b>you</b> <color=0x77ffffff><font size=10>to <b><color=0xffffffff></font>Burner Worm",
                "[ 2026.09.22 05:12:04 ] (bounty) <font size=12><b><color=0xff00aa00>5,000,000 ISK</b><color=0x77ffffff> added to next bounty payout"
        );

        List<ParsedKill> kills = GameLogKillParser.parseKills(lines);

        assertEquals(1, kills.size());
        assertEquals("Burner Worm", kills.get(0).npcName());
    }

    @Test
    void undecoratedMissLineNamesTheTargetWithNoMarkupAtAll() {
        List<String> lines = List.of(
                "[ 2026.09.22 05:20:10 ] (combat) Sansha's Demon misses you completely",
                "[ 2026.09.22 05:20:15 ] (bounty) <font size=12><b><color=0xff00aa00>1,000,000 ISK</b><color=0x77ffffff> added to next bounty payout"
        );

        List<ParsedKill> kills = GameLogKillParser.parseKills(lines);

        assertEquals(1, kills.size());
        assertEquals("Sansha's Demon", kills.get(0).npcName());
    }

    @Test
    void aBountyLineWithNoPrecedingTargetIsIgnored() {
        List<String> lines = List.of(
                "[ 2026.09.22 05:03:23 ] (hint) Attempting to join a channel",
                "[ 2026.09.22 05:12:04 ] (bounty) <font size=12><b><color=0xff00aa00>5,000,000 ISK</b><color=0x77ffffff> added to next bounty payout"
        );

        assertTrue(GameLogKillParser.parseKills(lines).isEmpty());
    }

    @Test
    void aKillAfterAStargateJumpIsAttributedToTheDestinationSystem() {
        List<String> lines = List.of(
                "[ 2026.08.03 10:03:50 ] (None) Jumping from 3MOG-V to TXW-EI",
                "[ 2026.09.22 07:52:59 ] (combat) <color=0xff00ffff><b>211</b> <color=0x77ffffff><font size=10>to</font> <b><color=0xffffffff>Burner Hawk</b><font size=10><color=0x77ffffff> - Mjolnir Rage Rocket - Hits",
                "[ 2026.09.22 07:52:59 ] (bounty) <font size=12><b><color=0xff00aa00>5,000,000 ISK</b><color=0x77ffffff> added to next bounty payout"
        );

        List<ParsedKill> kills = GameLogKillParser.parseKills(lines);

        assertEquals(1, kills.size());
        assertEquals("TXW-EI", kills.get(0).solarSystem());
    }

    @Test
    void aKillAfterUndockingIsAttributedToTheStationsSystemNotTheStationName() {
        List<String> lines = List.of(
                "[ 2026.09.22 05:06:23 ] (None) Undocking from Thashkarai VI - Zoar and Sons Factory to Thashkarai solar system.",
                "[ 2026.09.22 07:52:59 ] (combat) <color=0xff00ffff><b>211</b> <color=0x77ffffff><font size=10>to</font> <b><color=0xffffffff>Burner Hawk</b><font size=10><color=0x77ffffff> - Mjolnir Rage Rocket - Hits",
                "[ 2026.09.22 07:52:59 ] (bounty) <font size=12><b><color=0xff00aa00>5,000,000 ISK</b><color=0x77ffffff> added to next bounty payout"
        );

        List<ParsedKill> kills = GameLogKillParser.parseKills(lines);

        assertEquals(1, kills.size());
        assertEquals("Thashkarai", kills.get(0).solarSystem());
    }

    @Test
    void aKillBeforeAnyJumpOrUndockLineHasNoKnownSystem() {
        List<String> lines = List.of(
                "[ 2026.09.22 07:52:59 ] (combat) <color=0xff00ffff><b>211</b> <color=0x77ffffff><font size=10>to</font> <b><color=0xffffffff>Burner Hawk</b><font size=10><color=0x77ffffff> - Mjolnir Rage Rocket - Hits",
                "[ 2026.09.22 07:52:59 ] (bounty) <font size=12><b><color=0xff00aa00>5,000,000 ISK</b><color=0x77ffffff> added to next bounty payout"
        );

        List<ParsedKill> kills = GameLogKillParser.parseKills(lines);

        assertEquals(1, kills.size());
        assertEquals(null, kills.get(0).solarSystem());
    }

    @Test
    void classifiesKnownFactionSubstringsCaseInsensitively() {
        assertEquals("Sansha's Nation", GameLogKillParser.classifyFaction("Sansha's Nation Battleship"));
        assertEquals("Guristas Pirates", GameLogKillParser.classifyFaction("GURISTAS Scout"));
    }

    @Test
    void unrecognizedNameFallsBackToOtherFaction() {
        assertEquals(GameLogKillParser.OTHER_FACTION, GameLogKillParser.classifyFaction("Burner Hawk"));
    }

    @Test
    void officerSpawnsOfARecognizedFactionGetTheirOwnBucket() {
        assertEquals("Sansha's Nation Officers", GameLogKillParser.classifyFaction("Asteroid Sansha's Nation Officer"));
        assertEquals("Serpentis Officers", GameLogKillParser.classifyFaction("Asteroid Serpentis Officer"));
    }

    @Test
    void officerWithNoRecognizedFactionWordStillFallsBackToOther() {
        assertEquals(GameLogKillParser.OTHER_FACTION, GameLogKillParser.classifyFaction("Federation Navy Officer"));
    }

    @Test
    void theBountyLinesIskAmountIsKeptOnTheKill() {
        List<String> lines = List.of(
                "[ 2026.09.22 07:52:59 ] (combat) <color=0xff00ffff><b>211</b> <color=0x77ffffff><font size=10>to</font> <b><color=0xffffffff>Burner Hawk</b><font size=10><color=0x77ffffff> - Mjolnir Rage Rocket - Hits",
                "[ 2026.09.22 07:52:59 ] (bounty) <font size=12><b><color=0xff00aa00>5,000,000 ISK</b><color=0x77ffffff> added to next bounty payout"
        );

        assertEquals(5_000_000d, GameLogKillParser.parseKills(lines).get(0).bounty());
    }

    @Test
    void bountyAmountsReadWhateverGroupingTheClientLanguageUses() {
        assertEquals(5_000_000d, GameLogKillParser.parseBountyAmount("5,000,000 ISK added to next bounty payout"));
        assertEquals(5_000_000d, GameLogKillParser.parseBountyAmount("5.000.000 ISK added to next bounty payout"));
        assertEquals(250_000d, GameLogKillParser.parseBountyAmount("250 000 ISK added to next bounty payout"));
        assertEquals(12_345.67d, GameLogKillParser.parseBountyAmount("12,345.67 ISK added to next bounty payout"));
        assertEquals(null, GameLogKillParser.parseBountyAmount("added to next bounty payout"));
    }

    private static final List<String> OFFICER_FIGHT = List.of(
            "[ 2026.10.02 21:10:40 ] (None) Jumping from 3MOG-V to TXW-EI",
            "[ 2026.10.02 21:11:02 ] (combat) <color=0xffcc0000><b>120</b> <color=0x77ffffff><font size=10>from</font> <b><color=0xffffffff>Pithi Despoiler</b><font size=10><color=0x77ffffff> - Hits",
            "[ 2026.10.02 21:11:05 ] (combat) <color=0xff00ffff><b>900</b> <color=0x77ffffff><font size=10>to</font> <b><color=0xffffffff>Pithi Despoiler</b><font size=10><color=0x77ffffff> - Scourge Javelin Heavy Assault Missile - Hits",
            "[ 2026.10.02 21:11:06 ] (bounty) <font size=12><b><color=0xff00aa00>30,000 ISK</b><color=0x77ffffff> added to next bounty payout",
            "[ 2026.10.02 21:11:20 ] (combat) Estamel Tharchon misses you completely",
            "[ 2026.10.02 21:11:30 ] (combat) <color=0xffcc0000><b>350</b> <color=0x77ffffff><font size=10>from</font> <b><color=0xffffffff>Estamel Tharchon</b><font size=10><color=0x77ffffff> - Smashes",
            "[ 2026.10.02 21:12:00 ] (combat) <color=0xff00ffff><b>1060</b> <color=0x77ffffff><font size=10>to</font> <b><color=0xffffffff>Pithi Despoiler</b><font size=10><color=0x77ffffff> - Scourge Javelin Heavy Assault Missile - Hits",
            "[ 2026.10.02 21:12:01 ] (bounty) <font size=12><b><color=0xff00aa00>30,000 ISK</b><color=0x77ffffff> added to next bounty payout",
            "[ 2026.10.02 21:13:00 ] (combat) <color=0xff00ffff><b>1800</b> <color=0x77ffffff><font size=10>to</font> <b><color=0xffffffff>Estamel Tharchon</b><font size=10><color=0x77ffffff> - Scourge Javelin Heavy Assault Missile - Hits",
            "[ 2026.10.02 21:14:00 ] (combat) <color=0xffcc0000><b>410</b> <color=0x77ffffff><font size=10>from</font> <b><color=0xffffffff>Estamel Tharchon</b><font size=10><color=0x77ffffff> - Hits",
            "[ 2026.10.02 21:14:30 ] (combat) <color=0xff00ffff><b>2400</b> <color=0x77ffffff><font size=10>to</font> <b><color=0xffffffff>Estamel Tharchon</b><font size=10><color=0x77ffffff> - Scourge Javelin Heavy Assault Missile - Wrecks",
            "[ 2026.10.02 21:14:37 ] (bounty) <font size=12><b><color=0xff00aa00>12,500,000 ISK</b><color=0x77ffffff> added to next bounty payout"
    );

    @Test
    void aFightCollectsEveryNpcSeenWithKillsBountiesAndDamage() {
        List<ParsedEncounter> encounters = GameLogKillParser.parse(OFFICER_FIGHT).encounters();

        assertEquals(1, encounters.size());
        ParsedEncounter fight = encounters.get(0);
        assertEquals("TXW-EI", fight.solarSystem());
        assertEquals(Instant.parse("2026-10-02T21:11:02Z"), fight.startedAt());
        assertEquals(Instant.parse("2026-10-02T21:14:37Z"), fight.endedAt());
        assertEquals(List.of("Pithi Despoiler", "Estamel Tharchon"),
                fight.npcs().stream().map(ParsedEncounter.Npc::name).toList());

        ParsedEncounter.Npc escort = fight.npcs().get(0);
        assertEquals(2, escort.kills());
        assertEquals(60_000d, escort.bounty());
        assertEquals(1960, escort.damageDealt());
        assertEquals(120, escort.damageTaken());

        ParsedEncounter.Npc officer = fight.npcs().get(1);
        assertEquals(Instant.parse("2026-10-02T21:11:20Z"), officer.firstSeenAt(), "a miss still counts as seen");
        assertEquals(1, officer.kills());
        assertEquals(12_500_000d, officer.bounty());
        assertEquals(Instant.parse("2026-10-02T21:14:37Z"), officer.lastKillAt());
        assertEquals(4200, officer.damageDealt());
        assertEquals(760, officer.damageTaken());
    }

    @Test
    void aPauseLongerThanTheGapStartsANewFight() {
        List<String> lines = List.of(
                "[ 2026.10.02 21:00:00 ] (combat) <color=0xff00ffff><b>100</b> <color=0x77ffffff><font size=10>to</font> <b><color=0xffffffff>Pithi Despoiler</b><font size=10><color=0x77ffffff> - Hits",
                "[ 2026.10.02 21:01:00 ] (combat) <color=0xff00ffff><b>100</b> <color=0x77ffffff><font size=10>to</font> <b><color=0xffffffff>Pithi Despoiler</b><font size=10><color=0x77ffffff> - Hits",
                "[ 2026.10.02 21:02:01 ] (combat) <color=0xff00ffff><b>100</b> <color=0x77ffffff><font size=10>to</font> <b><color=0xffffffff>Pithi Despoiler</b><font size=10><color=0x77ffffff> - Hits"
        );

        List<ParsedEncounter> encounters = GameLogKillParser.parse(lines).encounters();

        assertEquals(2, encounters.size(), "exactly 60s apart continues the fight; 61s starts a new one");
        assertEquals(Instant.parse("2026-10-02T21:01:00Z"), encounters.get(0).endedAt());
        assertEquals(Instant.parse("2026-10-02T21:02:01Z"), encounters.get(1).startedAt());
    }

    @Test
    void aJumpEndsTheFightEvenWithinTheGap() {
        List<String> lines = List.of(
                "[ 2026.10.02 21:00:00 ] (combat) <color=0xff00ffff><b>100</b> <color=0x77ffffff><font size=10>to</font> <b><color=0xffffffff>Pithi Despoiler</b><font size=10><color=0x77ffffff> - Hits",
                "[ 2026.10.02 21:00:10 ] (None) Jumping from TXW-EI to 3MOG-V",
                "[ 2026.10.02 21:00:20 ] (combat) <color=0xff00ffff><b>100</b> <color=0x77ffffff><font size=10>to</font> <b><color=0xffffffff>Pithi Despoiler</b><font size=10><color=0x77ffffff> - Hits"
        );

        List<ParsedEncounter> encounters = GameLogKillParser.parse(lines).encounters();

        assertEquals(2, encounters.size());
        assertEquals(null, encounters.get(0).solarSystem());
        assertEquals("3MOG-V", encounters.get(1).solarSystem());
    }

    @Test
    void anOfficerKillEndToEndLandsInTheFactionOfficersBucket() {
        List<String> lines = List.of(
                "[ 2026.09.22 07:52:59 ] (combat) <color=0xff00ffff><b>211</b> <color=0x77ffffff><font size=10>to</font> <b><color=0xffffffff>Asteroid Sansha's Nation Officer</b><font size=10><color=0x77ffffff> - Mjolnir Rage Rocket - Hits",
                "[ 2026.09.22 07:52:59 ] (bounty) <font size=12><b><color=0xff00aa00>5,000,000 ISK</b><color=0x77ffffff> added to next bounty payout"
        );

        List<ParsedKill> kills = GameLogKillParser.parseKills(lines);

        assertEquals(1, kills.size());
        assertEquals("Asteroid Sansha's Nation Officer", kills.get(0).npcName());
        assertEquals("Sansha's Nation Officers", kills.get(0).factionLabel());
    }
}
