package com.evefarm.model;

import java.util.List;

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
        Double unitValue,
        int iconTypeId,
        List<RefinedMaterial> materials
) {
    public Double value() {
        return unitValue == null ? null : unitValue * quantity;
    }
}
