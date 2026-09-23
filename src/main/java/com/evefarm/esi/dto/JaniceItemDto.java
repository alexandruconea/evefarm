package com.evefarm.esi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JaniceItemDto(
        @JsonProperty("itemType") ItemType itemType,
        @JsonProperty("immediatePrices") Prices immediatePrices,
        @JsonProperty("top5AveragePrices") Prices top5AveragePrices
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ItemType(
            @JsonProperty("eid") int eid,
            @JsonProperty("name") String name
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Prices(
            @JsonProperty("buyPrice") Double buyPrice,
            @JsonProperty("sellPrice") Double sellPrice
    ) {
    }
}
