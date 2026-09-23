package com.evefarm.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AddDropDialogTest {

    @Test
    void readsBackThePriceItPrefillsAndPlainNumbers() {
        assertEquals(1_250_000_000d, AddDropDialog.parsePrice("1,250,000,000.00"));
        assertEquals(1_250_000_000d, AddDropDialog.parsePrice("1250000000"));
        assertEquals(99.5d, AddDropDialog.parsePrice(" 99.50 ISK"));
    }

    @Test
    void rejectsBlankNegativeAndNonNumericPrices() {
        assertNull(AddDropDialog.parsePrice(""));
        assertNull(AddDropDialog.parsePrice("-5"));
        assertNull(AddDropDialog.parsePrice("a lot"));
    }
}
