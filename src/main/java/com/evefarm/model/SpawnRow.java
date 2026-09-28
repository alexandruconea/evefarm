package com.evefarm.model;

import java.time.Instant;
import java.util.List;

public record SpawnRow(
        List<Long> encounterIds,
        String characterName,
        Instant startedAt,
        Instant endedAt,
        String solarSystem,
        String kind,
        int killed,
        double bounty,
        String composition,
        List<CharacterContribution> contributions
) {

    public long durationSeconds() {
        return Math.max(0, endedAt.getEpochSecond() - startedAt.getEpochSecond());
    }
}
