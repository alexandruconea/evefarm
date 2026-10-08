package com.evefarm.model;

import java.time.Instant;
import java.util.List;

public record EncounterSummary(
        long encounterId,
        String characterName,
        Instant startedAt,
        Instant endedAt,
        String solarSystem,
        List<ParsedEncounter.Npc> npcs
) {
}
