package com.evefarm.esi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LoyaltyOfferDto(
        @JsonProperty("offer_id") long offerId,
        @JsonProperty("type_id") int typeId,
        @JsonProperty("quantity") long quantity,
        @JsonProperty("lp_cost") long lpCost,
        @JsonProperty("isk_cost") double iskCost,
        @JsonProperty("ak_cost") double akCost,
        @JsonProperty("required_items") List<RequiredItemDto> requiredItems
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RequiredItemDto(
            @JsonProperty("type_id") int typeId,
            @JsonProperty("quantity") long quantity
    ) {
    }
}
