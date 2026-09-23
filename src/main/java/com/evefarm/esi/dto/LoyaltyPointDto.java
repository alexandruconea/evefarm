package com.evefarm.esi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LoyaltyPointDto(
        @JsonProperty("corporation_id") long corporationId,
        @JsonProperty("loyalty_points") long loyaltyPoints
) {
}
