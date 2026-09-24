package com.evefarm.model;

import java.time.Instant;

public record SpawnRow(
        long encounterId,
        String characterName,
        Instant startedAt,
        Instant endedAt,
        String solarSystem,
        String kind,
        int killed,
        double bounty,
        String composition
) {

    public long durationSeconds() {
        return Math.max(0, endedAt.getEpochSecond() - startedAt.getEpochSecond());
    }
}
