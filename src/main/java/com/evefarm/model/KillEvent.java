package com.evefarm.model;

import java.time.LocalDate;

public record KillEvent(
        LocalDate date,
        String factionLabel,
        String npcName,
        String solarSystem
) {
}
