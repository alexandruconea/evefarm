package com.evefarm.esi;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EsiHttpClientTest {

    @Test
    void retryAfterIsHonouredWithinSaneBounds() {
        assertEquals(12, EsiHttpClient.retryAfterSeconds(Optional.of("12")));
        assertEquals(5, EsiHttpClient.retryAfterSeconds(Optional.empty()), "no header -> default wait");
        assertEquals(5, EsiHttpClient.retryAfterSeconds(Optional.of("Wed, 21 Oct 2026 07:28:00 GMT")),
                "an HTTP-date isn't parsed -> default wait");
        assertEquals(60, EsiHttpClient.retryAfterSeconds(Optional.of("3600")), "never block a thread for an hour");
        assertEquals(1, EsiHttpClient.retryAfterSeconds(Optional.of("0")));
    }
}
