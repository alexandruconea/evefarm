package com.evefarm.model;

import java.time.Instant;

public record JournalPayout(Instant paidAt, double amount, String reason, String description) {

    public String solarSystem() {
        if (description == null) {
            return null;
        }
        int marker = description.lastIndexOf(" in ");
        return marker < 0 ? null : description.substring(marker + 4).trim();
    }
}
