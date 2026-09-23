package com.evefarm.esi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MarketPriceDto(
        @JsonProperty("type_id") int typeId,
        @JsonProperty("average_price") Double averagePrice,
        @JsonProperty("adjusted_price") Double adjustedPrice
) {
}
