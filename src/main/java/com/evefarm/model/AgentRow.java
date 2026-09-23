package com.evefarm.model;

public record AgentRow(
        long agentId,
        String agentName,
        Long corporationId,
        String corporationName,
        Long factionId,
        String factionName,
        String divisionName,
        String agentTypeName,
        int level,
        boolean isLocator,
        Long stationId,
        String stationName,
        String solarSystemName,
        Double security,
        String constellationName,
        String regionName
) {
}
