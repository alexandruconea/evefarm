package com.evefarm.model;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record KillDayTypeRow(
        LocalDate date,
        String factionLabel,
        String npcName,
        String solarSystem,
        int count
) {

    public static final String UNKNOWN_SYSTEM = "Unknown";

    public static List<KillDayTypeRow> groupByDayTypeAndSystem(List<KillEvent> events) {
        Map<KillEvent, Integer> counts = new LinkedHashMap<>();
        for (KillEvent event : events) {
            KillEvent normalized = event.solarSystem() == null
                    ? new KillEvent(event.date(), event.factionLabel(), event.npcName(), UNKNOWN_SYSTEM)
                    : event;
            counts.merge(normalized, 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .map(e -> new KillDayTypeRow(e.getKey().date(), e.getKey().factionLabel(), e.getKey().npcName(),
                        e.getKey().solarSystem(), e.getValue()))
                .sorted(Comparator.comparing(KillDayTypeRow::date).reversed()
                        .thenComparing(KillDayTypeRow::npcName)
                        .thenComparing(KillDayTypeRow::solarSystem))
                .toList();
    }
}
