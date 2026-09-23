package com.evefarm.esi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MarketOrderDto(
        @JsonProperty("order_id") long orderId,
        @JsonProperty("type_id") int typeId,
        @JsonProperty("volume_remain") long volumeRemain,
        @JsonProperty("volume_total") long volumeTotal,
        @JsonProperty("price") double price,
        @JsonProperty("is_buy_order") boolean isBuyOrder,
        @JsonProperty("escrow") Double escrow,
        @JsonProperty("state") String state,
        @JsonProperty("location_id") long locationId,
        @JsonProperty("issued") String issued,
        @JsonProperty("duration") Integer duration,
        @JsonProperty("range") String range,
        @JsonProperty("min_volume") Long minVolume
) {
}
