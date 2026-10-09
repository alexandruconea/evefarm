package com.evefarm.model;

import java.time.Instant;

public record OrderCompetition(long orderId, Double bestPrice, boolean outbid, Instant checkedAt) {
}
