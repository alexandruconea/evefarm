package com.evefarm.model;

public record StandingEntry(
        long fromId,
        String fromType,
        double standing
) {
}
