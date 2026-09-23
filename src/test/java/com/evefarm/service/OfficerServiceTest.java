package com.evefarm.service;

import com.evefarm.model.JournalPayout;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OfficerServiceTest {

    @Test
    void payoutReasonListsTypeIdsWithCounts() {
        Map<Integer, Integer> expected = new LinkedHashMap<>();
        expected.put(34155, 1);
        expected.put(16037, 3);
        assertEquals(expected, OfficerService.parseReason("34155: 1,16037: 3"));
        assertEquals(Map.of(), OfficerService.parseReason(null));
    }

    @Test
    void officerModulesAreNamedAfterTheFirstNameOrTheWholeDroneDesignation() {
        assertEquals("Estamel", OfficerService.officerItemPrefix("Estamel Tharchon"));
        assertEquals("Tobias", OfficerService.officerItemPrefix("Tobias Kruzhor"));
        assertEquals("Unit D-34343", OfficerService.officerItemPrefix("Unit D-34343"));
    }

    @Test
    void payoutSystemIsTheLastWordsOfTheDescription() {
        JournalPayout payout = new JournalPayout(Instant.parse("2026-09-23T12:01:30Z"), 9_700_000, "34155: 1",
                "Malpais Legate got bounty prizes for killing pirates in Aurohunen");
        assertEquals("Aurohunen", payout.solarSystem());
    }
}
