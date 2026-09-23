package com.evefarm.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TrackerSnapshotServiceTest {

    private final TrackerSnapshotService service = new TrackerSnapshotService(
            null, null, null, null, null, null, null, null, null, null, null, null, null, null);

    @Test
    void keepsIskPerLpWhenBookDepthCoversTheOffer() {
        assertEquals(9.3, service.liquidIskPerLp(9.3, 25.0, 10));
    }

    @Test
    void keepsIskPerLpWhenBookDepthExactlyMatchesTheOffer() {
        assertEquals(9.3, service.liquidIskPerLp(9.3, 10.0, 10));
    }

    @Test
    void dropsIskPerLpWhenBookDepthIsThinnerThanTheOffer() {
        assertNull(service.liquidIskPerLp(999999.0, 1.0, 10));
    }

    @Test
    void dropsIskPerLpWhenVolumeIsUnknown() {
        assertNull(service.liquidIskPerLp(9.3, null, 10));
    }

    @Test
    void staysNullWhenIskPerLpIsAlreadyNull() {
        assertNull(service.liquidIskPerLp(null, 10.0, 10));
    }
}
