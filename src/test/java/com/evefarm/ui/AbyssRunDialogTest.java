package com.evefarm.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AbyssRunDialogTest {

    @Test
    void theRunTimeIsReadAsMinutesAndSeconds() {
        assertEquals(872, AbyssRunDialog.parseDuration("14:32"));
        assertEquals(3_723, AbyssRunDialog.parseDuration("1:02:03"));
        assertEquals(900, AbyssRunDialog.parseDuration("15"));
        assertNull(AbyssRunDialog.parseDuration(""));
        assertNull(AbyssRunDialog.parseDuration("fourteen"));
        assertNull(AbyssRunDialog.parseDuration("1:2:3:4"));
    }

    @Test
    void iskAmountsAcceptSeparatorsAndTheIskSuffix() {
        assertEquals(12_500_000.0, AbyssRunDialog.parseIsk("12,500,000.00 ISK"));
        assertEquals(9_500.5, AbyssRunDialog.parseIsk(" 9 500.5 "));
        assertNull(AbyssRunDialog.parseIsk("-5"));
        assertNull(AbyssRunDialog.parseIsk("lots"));
        assertNull(AbyssRunDialog.parseIsk(""));
    }

    @Test
    void runTimesAreShownAsMinutesAndSeconds() {
        assertEquals("14:32", AbyssRunsTableModel.formatDuration(872));
        assertEquals("05:07", AbyssRunsTableModel.formatDuration(307));
        assertEquals("1:02:03", AbyssRunsTableModel.formatDuration(3_723));
        assertEquals("", AbyssRunsTableModel.formatDuration((Integer) null));
    }
}
