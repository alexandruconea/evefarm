package com.evefarm.model;

public record RefinedMaterial(
        int typeId,
        String name,
        double quantity,
        double volume,
        Double value
) {
}
