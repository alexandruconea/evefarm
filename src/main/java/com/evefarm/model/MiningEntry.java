package com.evefarm.model;

public record MiningEntry(
        String date,
        long solarSystemId,
        int typeId,
        long quantity
) {
}
