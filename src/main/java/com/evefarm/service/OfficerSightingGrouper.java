package com.evefarm.service;

import com.evefarm.model.OfficerSighting;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

final class OfficerSightingGrouper {

    static final Duration SAME_FIGHT = Duration.ofMinutes(2);

    private OfficerSightingGrouper() {
    }

    static List<OfficerSighting> group(List<OfficerSighting> sightings) {
        List<OfficerSighting> sorted = new ArrayList<>(sightings);
        sorted.sort(Comparator.comparing(OfficerSighting::firstSeenAt));
        List<List<OfficerSighting>> groups = new ArrayList<>();
        for (OfficerSighting sighting : sorted) {
            List<OfficerSighting> target = null;
            for (List<OfficerSighting> group : groups) {
                if (belongsTo(sighting, group)) {
                    target = group;
                    break;
                }
            }
            if (target == null) {
                target = new ArrayList<>();
                groups.add(target);
            }
            target.add(sighting);
        }
        List<OfficerSighting> merged = new ArrayList<>();
        for (List<OfficerSighting> group : groups) {
            merged.add(merge(group));
        }
        merged.sort(Comparator.comparing(OfficerSighting::earliestSeenAt).reversed());
        return merged;
    }

    private static boolean belongsTo(OfficerSighting sighting, List<OfficerSighting> group) {
        OfficerSighting first = group.get(0);
        if (!first.officerName().equals(sighting.officerName()) || sighting.solarSystem() == null
                || !sighting.solarSystem().equals(first.solarSystem())) {
            return false;
        }
        Instant groupEnd = group.stream().map(OfficerSighting::fightEndedAt).max(Comparator.naturalOrder())
                .orElse(first.fightEndedAt());
        return !sighting.fightStartedAt().isAfter(groupEnd.plus(SAME_FIGHT));
    }

    static OfficerSighting merge(List<OfficerSighting> group) {
        if (group.size() == 1) {
            return group.get(0);
        }
        OfficerSighting primary = group.stream().filter(OfficerSighting::killed)
                .max(Comparator.comparingDouble(OfficerSighting::officerBounty))
                .orElse(group.get(0));
        List<OfficerSighting.Member> members = group.stream()
                .flatMap(sighting -> sighting.members().stream())
                .sorted(Comparator.comparing(OfficerSighting.Member::killed).reversed()
                        .thenComparing(Comparator.comparingLong(OfficerSighting.Member::damageDealt).reversed())
                        .thenComparing(OfficerSighting.Member::firstSeenAt))
                .toList();
        Set<String> names = new LinkedHashSet<>();
        members.forEach(member -> names.add(member.characterName()));
        Instant killedAt = group.stream().map(OfficerSighting::killedAt).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);
        return new OfficerSighting(
                primary.encounterId(),
                primary.characterId(),
                String.join(", ", names),
                primary.officerName(),
                primary.officerGroup(),
                primary.firstSeenAt(),
                killedAt,
                group.stream().mapToDouble(OfficerSighting::officerBounty).sum(),
                primary.solarSystem(),
                group.stream().map(OfficerSighting::fightStartedAt).min(Comparator.naturalOrder()).orElseThrow(),
                group.stream().map(OfficerSighting::fightEndedAt).max(Comparator.naturalOrder()).orElseThrow(),
                group.stream().mapToInt(OfficerSighting::escortKills).sum(),
                group.stream().mapToDouble(OfficerSighting::dropValue).sum(),
                firstPresent(primary.belt(), group.stream().map(OfficerSighting::belt).toList()),
                firstPresent(primary.notes(), group.stream().map(OfficerSighting::notes).toList()),
                primary.payout() != null ? primary.payout()
                        : group.stream().map(OfficerSighting::payout).filter(Objects::nonNull).findFirst().orElse(null),
                members);
    }

    private static String firstPresent(String preferred, List<String> others) {
        if (preferred != null) {
            return preferred;
        }
        return others.stream().filter(Objects::nonNull).findFirst().orElse(null);
    }
}
