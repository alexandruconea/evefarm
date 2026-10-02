package com.evefarm.esi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StandingDto(
        @JsonProperty("from_id") long fromId,
        @JsonProperty("from_type") String fromType,
        @JsonProperty("standing") double standing
) {
}
