package com.evefarm.model;

import java.time.Instant;

public record TokenRecord(
        long characterId,
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
