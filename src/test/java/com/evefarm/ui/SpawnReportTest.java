package com.evefarm.ui;

import com.evefarm.model.CharacterContribution;
import com.evefarm.model.ParsedEncounter;
import com.evefarm.model.SpawnClass;
import com.evefarm.model.SpawnMember;
import com.evefarm.model.SpawnRow;
import org.junit.jupiter.api.Test;

import javax.swing.table.DefaultTableModel;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpawnReportTest {

    private static final Instant SAVED = Instant.parse("2026-10-06T12:00:00Z");
    private static final Instant START = Instant.parse("2026-10-05T21:10:00Z");

    @Test
    void numbersAndIskLineUpOnTheRight() {
        DefaultTableModel model = new DefaultTableModel(new Object[][]{
                {"Alpha", 1200, "1,000.00 ISK"},
                {"Be", 3, ""}}, new Object[]{"Name", "Count", "Bounty"}) {
            @Override
            public Class<?> getColumnClass(int column) {
                return column == 1 ? Integer.class : String.class;
            }
        };

        assertEquals(List.of(
                "Name   Count        Bounty",
                "-----  -----  ------------",
                "Alpha  1,200  1,000.00 ISK",
                "Be         3"), SpawnReport.table(model));
    }

    @Test
    void eachSpawnIsWrittenWithItsFightersAndNpcs() {
        SpawnRow spawn = new SpawnRow(List.of(1L, 2L), "Alpha, Beta", START, START.plusSeconds(192), "Tama",
                "Officer", 3, 1_500_000, "2× Coreli Guardian Agent, 1× Estamel Tharchon",
                List.of(new CharacterContribution("Alpha", 2, 1_000_000, 12_345),
                        new CharacterContribution("Beta", 1, 500_000, 678)));
        List<SpawnMember> members = List.of(
                new SpawnMember(new ParsedEncounter.Npc("Estamel Tharchon", START.plusSeconds(5),
                        START.plusSeconds(120), 1, 1_000_000, START.plusSeconds(120), 12_345, 2_000),
                        "Asteroid Serpentis Officer", SpawnClass.OFFICER),
                new SpawnMember(new ParsedEncounter.Npc("Coreli Guardian Agent", START.plusSeconds(5),
                        START.plusSeconds(150), 2, 500_000, START.plusSeconds(150), 678, 0), null,
                        SpawnClass.OTHER));

        List<String> lines = SpawnReport.lines(List.of(spawn), row -> members, SAVED);

        assertEquals(List.of(
                "EVE Farm - Spawns",
                "Saved 2026-10-06 12:00 EVE",
                "1 spawn, 3 NPCs killed, 1,500,000.00 ISK bounty",
                "From 2026-10-05 21:10 to 2026-10-05 21:13 EVE",
                "",
                "=".repeat(80),
                "Spawn 1 of 1",
                "2026-10-05 21:10 EVE  ·  Tama  ·  Officer  ·  3 killed  ·  1,500,000.00 ISK  ·  03:12",
                "Characters: Alpha, Beta",
                "Fought by Alpha: 2 kills, 1,000,000.00 ISK, 12,345 damage  ·  Beta: 1 kill, 500,000.00 ISK, "
                        + "678 damage",
                ""), lines.subList(0, 11));
        assertTrue(lines.get(11).startsWith("NPC "));
        assertTrue(lines.get(13).startsWith("Estamel Tharchon       Officer  Serpentis Officer"));
        assertTrue(lines.get(14).startsWith("Coreli Guardian Agent  Other"));
        assertTrue(lines.get(14).endsWith("678             0"));
        assertEquals(15, lines.size());
    }

    @Test
    void anEmptyListStillSaysSo() {
        assertEquals(List.of("EVE Farm - Spawns", "Saved 2026-10-06 12:00 EVE",
                "0 spawns, 0 NPCs killed, 0.00 ISK bounty"), SpawnReport.lines(List.of(), row -> List.of(), SAVED));
    }
}
