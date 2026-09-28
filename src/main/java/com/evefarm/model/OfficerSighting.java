package com.evefarm.model;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

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
        double dropValue,
        String belt,
        String notes,
        JournalPayout payout,
        List<Member> members
) {
    public record Member(long characterId, String characterName, long encounterId, Instant firstSeenAt,
                         Instant killedAt, double bounty, long damageDealt) {
        public boolean killed() {
            return killedAt != null;
        }
    }

    public boolean killed() {
        return killedAt != null;
    }

    public double totalValue() {
        return officerBounty + dropValue;
    }

    public List<Long> encounterIds() {
        return members.stream().map(Member::encounterId).distinct().toList();
    }

    public Instant earliestSeenAt() {
        return members.stream().map(Member::firstSeenAt).min(Comparator.naturalOrder()).orElse(firstSeenAt);
    }

    public boolean sameAs(OfficerSighting other) {
        return officerName.equals(other.officerName) && members.stream().anyMatch(mine -> other.members.stream()
                .anyMatch(theirs -> theirs.characterId() == mine.characterId()
                        && Objects.equals(theirs.firstSeenAt(), mine.firstSeenAt())));
    }

    public OfficerSighting withOfficerGroup(String group) {
        return new OfficerSighting(encounterId, characterId, characterName, officerName, group, firstSeenAt,
                killedAt, officerBounty, solarSystem, fightStartedAt, fightEndedAt, escortKills,
                dropValue, belt, notes, payout, members);
    }

    public OfficerSighting withPayout(JournalPayout newPayout) {
        return new OfficerSighting(encounterId, characterId, characterName, officerName, officerGroup, firstSeenAt,
                killedAt, officerBounty, solarSystem, fightStartedAt, fightEndedAt, escortKills,
                dropValue, belt, notes, newPayout, members);
    }
}
