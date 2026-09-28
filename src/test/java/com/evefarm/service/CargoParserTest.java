package com.evefarm.service;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CargoParserTest {

    @Test
    void anEveCargoCopyGivesTheNameAndQuantityOfEachLine() {
        String copied = "Triglavian Survey Database\t12\tTriglavian Datastream\t\t\t1.20 m3\t1,200,000.00 ISK\r\n"
                + "Tritanium\t1,234,567\tMineral\t\t\t12,345.67 m3\t4,938,268.00 ISK\n"
                + "Gila\t\tCruiser\t\t\t10,000 m3\n";

        Map<String, Long> cargo = CargoParser.parse(copied);

        assertEquals(Map.of("Triglavian Survey Database", 12L, "Tritanium", 1_234_567L, "Gila", 1L), cargo);
    }

    @Test
    void timesNotationAndPlainNamesAreUnderstood() {
        Map<String, Long> cargo = CargoParser.parse("""
                Tritanium x 1,000
                25 x Pyerite
                Agitated Exotic Filament
                """);

        assertEquals(Map.of("Tritanium", 1_000L, "Pyerite", 25L, "Agitated Exotic Filament", 1L), cargo);
    }

    @Test
    void theSameItemOnSeveralLinesIsAddedUpWhateverTheCase() {
        Map<String, Long> cargo = CargoParser.parse("Tritanium\t100\nTRITANIUM\t50\n\n   \n");

        assertEquals(Map.of("Tritanium", 150L), cargo);
    }

    @Test
    void onlyWhatWasGainedCountsAsLoot() {
        Map<String, Long> before = new LinkedHashMap<>();
        before.put("Tritanium", 100L);
        before.put("Agitated Exotic Filament", 3L);
        before.put("Nanite Repair Paste", 50L);
        Map<String, Long> after = new LinkedHashMap<>();
        after.put("tritanium", 160L);
        after.put("Agitated Exotic Filament", 2L);
        after.put("Nanite Repair Paste", 50L);
        after.put("Triglavian Survey Database", 7L);

        assertEquals(Map.of("tritanium", 60L, "Triglavian Survey Database", 7L), CargoParser.gained(before, after));
    }

    @Test
    void onlyTabSeparatedLinesLookLikeACopyFromEve() {
        assertTrue(CargoParser.looksLikeEveCopy("Tritanium\t1,000\tMineral\r\nGila\t\tCruiser\n\n"));
        assertFalse(CargoParser.looksLikeEveCopy("Tritanium\t1,000\nsee you in local"));
        assertFalse(CargoParser.looksLikeEveCopy("\tTritanium\t1,000"));
        assertFalse(CargoParser.looksLikeEveCopy("https://zkillboard.com/"));
        assertFalse(CargoParser.looksLikeEveCopy("  \n "));
        assertFalse(CargoParser.looksLikeEveCopy(null));
        assertFalse(CargoParser.looksLikeEveCopy("Tritanium\t1\n".repeat(2_001)));
    }

    @Test
    void emptyTextHasNoItems() {
        assertEquals(Map.of(), CargoParser.parse(null));
        assertEquals(Map.of(), CargoParser.parse(""));
    }
}
