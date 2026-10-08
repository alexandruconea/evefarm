package com.evefarm.model;

import java.time.Instant;

public record TokenRecord(
        String refreshToken,
        String accessToken,
        Instant accessTokenExpiresAt
) {
    public boolean isAccessTokenValid() {
        return accessToken != null
                && accessTokenExpiresAt != null
                && accessTokenExpiresAt.isAfter(Instant.now().plusSeconds(60));
    }
}
