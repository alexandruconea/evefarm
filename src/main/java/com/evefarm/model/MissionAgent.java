package com.evefarm.model;

public record MissionAgent(long agentId, String agentName, Long corporationId, String corporationName, Long factionId,
                           String factionName) {
}
