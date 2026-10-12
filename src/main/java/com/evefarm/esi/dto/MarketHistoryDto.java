package com.evefarm.esi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MarketHistoryDto(
        @JsonProperty("date") String date,
        @JsonProperty("volume") long volume,
        @JsonProperty("average") double average,
        @JsonProperty("highest") double highest,
        @JsonProperty("lowest") double lowest
) {
}
