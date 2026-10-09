package com.evefarm.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextTest {

    @Test
    void htmlSpecialCharactersAreEscaped() {
        assertEquals("Zainou 'Snapshot' &lt;b&gt; &amp; &quot;x&quot;",
                Text.escapeHtml("Zainou 'Snapshot' <b> & \"x\""));
    }

    @Test
    void identifiersFromEveBecomeTitleCaseWords() {
        assertEquals("Item Exchange", Text.titleCase("item_exchange"));
        assertEquals("Bounty Prizes", Text.titleCase("bounty_prizes"));
        assertEquals("Hangar All", Text.titleCase("HangarAll"));
        assertEquals("Hi Slot0", Text.titleCase("HiSlot0"));
        assertEquals("", Text.titleCase(null));
        assertEquals("", Text.titleCase("  "));
    }
}
