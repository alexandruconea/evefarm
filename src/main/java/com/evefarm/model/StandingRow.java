package com.evefarm.model;

public record StandingRow(
        long characterId,
        String characterName,
        long fromId,
        String fromType,
        String name,
        double standing,
        Long corporationId,
        String corporationName,
        Long factionId,
        String factionName,
        Integer level,
        String divisionName,
        String systemName
) {
}
