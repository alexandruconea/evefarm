package com.evefarm.ui;

import com.evefarm.model.MissionAgent;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

record StandingTarget(Long agentId, List<Long> groupIds, String characterName) {

    static StandingTarget of(EveEntity entity, MissionAgent agent, String characterName) {
        Long corporationId = agent == null ? null : agent.corporationId();
        Long factionId = agent == null ? null : agent.factionId();
        return switch (entity.kind()) {
            case AGENT -> new StandingTarget(entity.id(), ids(corporationId, factionId), characterName);
            case CORPORATION -> new StandingTarget(null, ids(entity.id(), factionId), characterName);
            case FACTION -> new StandingTarget(null, ids(entity.id()), characterName);
            case CHARACTER, ALLIANCE, OTHER -> new StandingTarget(null, List.of(), characterName);
        };
    }

    private static List<Long> ids(Long... ids) {
        return Stream.of(ids).filter(Objects::nonNull).toList();
    }
}
