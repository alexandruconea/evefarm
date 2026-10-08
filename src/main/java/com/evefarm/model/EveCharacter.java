package com.evefarm.model;

import java.time.Instant;
import java.util.List;

public record EveCharacter(
        long characterId,
        String characterName,
        List<String> scopes,
        Instant addedAt
) {
}
