package com.evefarm.model;

import java.time.Instant;

public record LpGain(
        long characterId,
        String characterName,
        String corporationName,
        long points,
        Instant seenAt,
        Instant since
) {
}
