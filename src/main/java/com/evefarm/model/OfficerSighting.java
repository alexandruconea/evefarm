package com.evefarm.model;

import java.time.Instant;

public record OfficerSighting(
        long encounterId,
        long characterId,
        String characterName,
        String officerName,
        String officerGroup,
        Instant firstSeenAt,
        Instant killedAt,
        double officerBounty,
        String solarSystem,
        Instant fightStartedAt,
        Instant fightEndedAt,
        int escortKills,
        int escortTypes,
        double dropValue,
        String belt,
        String notes,
        JournalPayout payout
) {
    public boolean killed() {
        return killedAt != null;
    }

    public double totalValue() {
        return officerBounty + dropValue;
    }

    public OfficerSighting withOfficerGroup(String group) {
        return new OfficerSighting(encounterId, characterId, characterName, officerName, group, firstSeenAt,
                killedAt, officerBounty, solarSystem, fightStartedAt, fightEndedAt, escortKills, escortTypes,
                dropValue, belt, notes, payout);
    }

    public OfficerSighting withPayout(JournalPayout newPayout) {
        return new OfficerSighting(encounterId, characterId, characterName, officerName, officerGroup, firstSeenAt,
                killedAt, officerBounty, solarSystem, fightStartedAt, fightEndedAt, escortKills, escortTypes,
                dropValue, belt, notes, newPayout);
    }
}
