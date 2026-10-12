package com.evefarm.model;

import java.time.Instant;

public record LoyaltyPointCheck(Instant dataFrom, Instant nextRead) {
}
