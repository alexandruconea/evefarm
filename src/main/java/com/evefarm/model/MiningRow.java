package com.evefarm.model;

public record MiningRow(
        long characterId,
        String characterName,
        String date,
        String systemName,
        int typeId,
        String oreName,
        String kind,
        long quantity,
        double volume,
        Double unitValue
) {
    public Double value() {
        return unitValue == null ? null : unitValue * quantity;
    }
}
