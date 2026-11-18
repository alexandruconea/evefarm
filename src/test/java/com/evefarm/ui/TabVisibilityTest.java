package com.evefarm.ui;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TabVisibilityTest {

    private static final Map<String, List<String>> GROUPS = groups();

    private static Map<String, List<String>> groups() {
        Map<String, List<String>> groups = new LinkedHashMap<>();
        groups.put("group.values", List.of("values", "tracker", "assets"));
        groups.put("group.wallet", List.of("journal", "transactions", "marketOrders", "contracts"));
        groups.put("lpStore", List.of("lpStore"));
        groups.put("kills", List.of("kills"));
        groups.put("abyss", List.of("abyss"));
        groups.put("group.industry", List.of("industry", "industryJobs", "mining"));
        groups.put("group.standings", List.of("standings", "agents"));
        groups.put("skills", List.of("skills"));
        return groups;
    }

    @Test
    void withNothingSavedTheTabsComeInTheirUsualOrder() {
        assertEquals(List.copyOf(GROUPS.keySet()), TabVisibility.orderGroups(List.of(), GROUPS));
    }

    @Test
    void anOrderSavedBeforeTabsHadTabsInsideStartsFromTheUsualOrder() {
        List<String> saved = List.of("skills", "assets", "tracker", "journal", "marketOrders", "transactions",
                "contracts", "industryJobs", "values", "lpStore", "kills", "abyss", "mining", "agents", "standings",
                "industry");

        assertEquals(List.copyOf(GROUPS.keySet()), TabVisibility.orderGroups(saved, GROUPS));
    }

    @Test
    void aSavedOrderOfTabsIsKeptAndANewTabGoesAfterTheOneBeforeIt() {
        List<String> saved = List.of("skills", "group.wallet", "abyss", "group.values", "lpStore", "kills",
                "group.standings", "gone");

        assertEquals(List.of("skills", "group.wallet", "abyss", "group.industry", "group.values", "lpStore",
                "kills", "group.standings"), TabVisibility.orderGroups(saved, GROUPS));
    }
}
