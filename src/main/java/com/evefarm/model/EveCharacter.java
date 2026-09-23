package com.evefarm.model;

import java.time.Instant;
import java.util.List;

public record EveCharacter(
        long characterId,
        String characterName,
        Long corporationId,
        List<String> scopes,
        Instant addedAt,
        boolean enabled
) {
}
