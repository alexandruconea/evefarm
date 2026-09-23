package com.evefarm.auth;

import java.time.Instant;
import java.util.List;

public record CharacterIdentity(
        long characterId,
        String characterName,
        List<String> scopes,
        Instant expiresAt,
        String ownerHash
) {
}
