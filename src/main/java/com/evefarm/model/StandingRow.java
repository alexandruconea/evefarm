package com.evefarm.model;

public record StandingRow(
        long characterId,
        String characterName,
        long fromId,
        String fromType,
        String name,
        double standing,
        String corporationName,
        String factionName,
        Integer level,
        String divisionName,
        String systemName
) {
}
