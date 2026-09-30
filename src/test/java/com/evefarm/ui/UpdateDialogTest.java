package com.evefarm.ui;

import com.evefarm.esi.EsiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateDialogTest {

    @Test
    void aFailureIsDescribedWithItsRootCause() {
        IllegalStateException error = new IllegalStateException("Failed to refresh journal",
                new IllegalStateException("No stored token for character 2124165849"));

        assertEquals("Failed to refresh journal (No stored token for character 2124165849)",
                UpdateDialog.describe(error));
    }

    @Test
    void eveBeingDownIsSaidPlainly() {
        String description = UpdateDialog.describe(new IllegalStateException("Failed to refresh assets",
                new EsiException(504, "{\"error\":\"Timeout contacting tranquility\",\"timeout\":10}")));

        assertEquals("EVE's servers didn't answer (HTTP 504). Around 11:00 EVE time that's the daily downtime - "
                + "try again in a few minutes.", description);
    }

    @Test
    void aVeryLongMessageIsCutShort() {
        String description = UpdateDialog.describe(new EsiException(500, "x".repeat(2000)));

        assertTrue(description.length() <= 303);
        assertTrue(description.endsWith("..."));
    }
}
