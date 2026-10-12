package com.evefarm.ui;

import com.evefarm.esi.EsiException;
import com.evefarm.ui.DataUpdater.Category;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataUpdaterTest {

    private static final Instant NOW = Instant.parse("2026-10-12T12:00:00Z");
    private static final Category ASSETS = category("assets", "Assets", Duration.ofHours(1));
    private static final Category JOURNAL = category("journal", "Journal", Duration.ofHours(1));
    private static final Category SKILLS = category("skills", "Skills", Duration.ofMinutes(5));
    private static final List<Category> CATEGORIES = List.of(ASSETS, JOURNAL, SKILLS);

    private static Category category(String key, String label, Duration cooldown) {
        return new Category(key, label, cooldown, (characters, failures) -> {
        });
    }

    @Test
    void aFailureIsDescribedWithItsRootCause() {
        IllegalStateException error = new IllegalStateException("Failed to refresh journal",
                new IllegalStateException("No stored token for character 2124165849"));

        assertEquals("Failed to refresh journal (No stored token for character 2124165849)",
                DataUpdater.describe(error));
    }

    @Test
    void eveBeingDownIsSaidPlainly() {
        String description = DataUpdater.describe(new IllegalStateException("Failed to refresh assets",
                new EsiException(504, "{\"error\":\"Timeout contacting tranquility\",\"timeout\":10}")));

        assertEquals("EVE's servers didn't answer (HTTP 504). Around 11:00 EVE time that's the daily downtime - "
                + "try again in a few minutes.", description);
    }

    @Test
    void aVeryLongMessageIsCutShort() {
        String description = DataUpdater.describe(new EsiException(500, "x".repeat(2000)));

        assertTrue(description.length() <= 303);
        assertTrue(description.endsWith("..."));
    }

    @Test
    void dataNeverUpdatedCanBeUpdatedRightAway() {
        assertEquals(Instant.EPOCH, DataUpdater.readyAt(ASSETS, Map.of()));
        assertEquals("Click to update all your data.", DataUpdater.status(CATEGORIES, Map.of(), Set.of(), null, NOW));
    }

    @Test
    void theButtonSaysWhatCanBeUpdatedAndWhenTheRestCan() {
        String status = DataUpdater.status(CATEGORIES, Map.of("assets", NOW.minus(Duration.ofMinutes(20))),
                Set.of(), null, NOW);

        assertEquals("Click to update Journal, Skills.\n\nCan be updated again in:\n40 min: Assets", status);
    }

    @Test
    void whenEverythingWasJustUpdatedItSaysWhenTheNextUpdateCanRun() {
        Map<String, Instant> lastRefreshed = Map.of(
                "assets", NOW.minus(Duration.ofMinutes(18)),
                "journal", NOW.minus(Duration.ofMinutes(18)).minusSeconds(20),
                "skills", NOW.minus(Duration.ofMinutes(3)));

        assertEquals("Everything was updated recently. The next update can run in 2 min.\n\n"
                        + "Can be updated again in:\n2 min: Skills\n42 min: Journal, Assets",
                DataUpdater.status(CATEGORIES, lastRefreshed, Set.of(), null, NOW));
    }

    @Test
    void whileUpdatingItSaysWhatRunsAndWhatComesNext() {
        assertEquals("Updating Assets...\nNext: Journal",
                DataUpdater.status(CATEGORIES, Map.of(), Set.of("assets", "journal"), ASSETS, NOW));
    }

    @Test
    void waitsAreRoundedUpToTheMinute() {
        assertEquals("45 s", DataUpdater.waitText(Duration.ofSeconds(45)));
        assertEquals("1 s", DataUpdater.waitText(Duration.ZERO));
        assertEquals("2 min", DataUpdater.waitText(Duration.ofSeconds(61)));
        assertEquals("1 h 00 min", DataUpdater.waitText(Duration.ofHours(1)));
        assertEquals("1 h 01 min", DataUpdater.waitText(Duration.ofSeconds(3601)));
    }
}
