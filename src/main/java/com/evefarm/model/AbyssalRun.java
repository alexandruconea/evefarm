package com.evefarm.model;

import java.time.Instant;

public record AbyssalRun(
        long id,
        long characterId,
        String characterName,
        Instant startedAt,
        Integer durationSeconds,
        AbyssTier tier,
        AbyssWeather weather,
        Integer shipTypeId,
        String shipName,
        boolean survived,
        double lootValue,
        Double filamentCost,
        String notes
) {
    public double profit() {
        return lootValue - (filamentCost == null ? 0 : filamentCost);
    }

    public Double iskPerHour() {
        return durationSeconds == null || durationSeconds <= 0 ? null : profit() * 3600.0 / durationSeconds;
    }

    public AbyssalRun withLootValue(double newLootValue) {
        return new AbyssalRun(id, characterId, characterName, startedAt, durationSeconds, tier, weather,
                shipTypeId, shipName, survived, newLootValue, filamentCost, notes);
    }

    public AbyssalRun withId(long newId) {
        return new AbyssalRun(newId, characterId, characterName, startedAt, durationSeconds, tier, weather,
                shipTypeId, shipName, survived, lootValue, filamentCost, notes);
    }
}
