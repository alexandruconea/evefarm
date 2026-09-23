package com.evefarm.model;

import java.time.Instant;
import java.util.List;

public record ParsedEncounter(Instant startedAt, Instant endedAt, String solarSystem, List<Npc> npcs) {

    public record Npc(String name, Instant firstSeenAt, Instant lastSeenAt, int kills, double bounty,
                      Instant lastKillAt, long damageDealt, long damageTaken) {
    }
}
