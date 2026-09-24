package com.evefarm.esi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FuzzworkAggregateDto(
        @JsonProperty("buy") Side buy,
        @JsonProperty("sell") Side sell
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Side(
            @JsonProperty("weightedAverage") String weightedAverage,
            @JsonProperty("max") String max,
            @JsonProperty("min") String min,
            @JsonProperty("median") String median,
            @JsonProperty("volume") String volume,
            @JsonProperty("percentile") String percentile
    ) {
    }
}
