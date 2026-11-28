package com.evefarm.model;

public record BlueprintEntry(long itemId, int typeId, Long locationId, String locationFlag, int quantity,
                             int materialEfficiency, int timeEfficiency, int runs) {
}
