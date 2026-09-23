package com.evefarm.model;

import java.time.Instant;

public record ParsedKill(
        Instant killedAt,
        String npcName,
        String factionLabel,
        String solarSystem,
        Double bounty
) {
    public ParsedKill withFactionLabel(String label) {
        return new ParsedKill(killedAt, npcName, label, solarSystem, bounty);
    }
}
