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
    void aVeryLongMessageIsCutShort() {
        String description = UpdateDialog.describe(new EsiException(500, "x".repeat(2000)));

        assertTrue(description.length() <= 303);
        assertTrue(description.endsWith("..."));
    }
}
