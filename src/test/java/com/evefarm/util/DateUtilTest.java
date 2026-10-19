package com.evefarm.util;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DateUtilTest {

    @Test
    void isoInstantsAreReadAndAnythingElseIsLeftOut() {
        assertEquals(Optional.of(Instant.parse("2026-10-19T10:00:00Z")), DateUtil.parseInstant("2026-10-19T10:00:00Z"));
        assertEquals(Optional.empty(), DateUtil.parseInstant(null));
        assertEquals(Optional.empty(), DateUtil.parseInstant(" "));
        assertEquals(Optional.empty(), DateUtil.parseInstant("2026.10.19 10:00"));
    }

    @Test
    void anIsoInstantThatCantBeReadIsShownAsItIs() {
        assertEquals("", DateUtil.formatIsoInstant(null));
        assertEquals("yesterday", DateUtil.formatIsoInstant("yesterday"));
    }
}
