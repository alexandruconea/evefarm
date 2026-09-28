package com.evefarm.service;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AggroLineParserTest {

    @Test
    void damageTakenNamesTheAttacker() {
        assertEquals(Optional.of("Lucifer Dramiel"), AggroLineParser.attacker("[ 2026.09.28 11:21:43 ] (combat) "
                + "<color=0xffcc0000><b>13</b> <color=0x77ffffff><font size=10>from</font> <b><color=0xffffffff>"
                + "Lucifer Dramiel</b><font size=10><color=0x77ffffff> - Hits\r"));
    }

    @Test
    void aMissIsAggroToo() {
        assertEquals(Optional.of("Lucifer Swordspine"),
                AggroLineParser.attacker("[ 2026.09.28 11:21:37 ] (combat) Lucifer Swordspine misses you completely"));
    }

    @Test
    void warpScramblesAndIncomingNeutsCount() {
        assertEquals(Optional.of("Lucifer Echo"), AggroLineParser.attacker("[ 2026.09.28 11:22:06 ] (combat) "
                + "<color=0xffffffff><b>Warp scramble attempt</b> <color=0x77ffffff><font size=10>from</font> "
                + "<color=0xffffffff><b>Lucifer Echo</b> <color=0x77ffffff><font size=10>to <b><color=0xffffffff>"
                + "</font>you!"));
        assertEquals(Optional.of("Lucid Sentinel"), AggroLineParser.attacker("[ 2026.09.28 11:39:23 ] (combat) "
                + "<color=0xffe57f7f><b>5 GJ</b><color=0x77ffffff><font size=10> energy neutralized </font><b>"
                + "<color=0xffffffff>Lucid Sentinel</b><color=0x77ffffff><font size=10> - Lucid Sentinel</font>"));
    }

    @Test
    void ourOwnShotsRepairsAndOtherLinesAreNotAggro() {
        assertEquals(Optional.empty(), AggroLineParser.attacker(
                "[ 2026.09.28 11:21:44 ] (combat) 212 to Lucifer Dramiel - Small Focused Beam Laser II - Penetrates"));
        assertEquals(Optional.empty(), AggroLineParser.attacker(
                "[ 2026.09.28 11:21:44 ] (combat) Malpais Legate misses Renewing Rodiva completely - Small Focused Beam Laser II"));
        assertEquals(Optional.empty(), AggroLineParser.attacker(
                "[ 2026.09.28 11:21:45 ] (combat) 64 remote armor repaired by Nozeu - Small Remote Armor Repairer II"));
        assertEquals(Optional.empty(), AggroLineParser.attacker(
                "[ 2026.09.28 11:21:45 ] (combat) 30 GJ energy neutralized Lucid Sentinel - Small Energy Neutralizer II"));
        assertEquals(Optional.empty(), AggroLineParser.attacker(
                "[ 2026.09.28 11:21:46 ] (notify) Lucifer Dramiel misses you completely"));
        assertEquals(Optional.empty(), AggroLineParser.attacker("  Listener: Malpais Legate"));
    }
}
