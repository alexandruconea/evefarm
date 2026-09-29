package com.evefarm.esi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ContractItemDto(
        @JsonProperty("record_id") long recordId,
        @JsonProperty("type_id") int typeId,
        @JsonProperty("quantity") long quantity,
        @JsonProperty("raw_quantity") Integer rawQuantity,
        @JsonProperty("is_included") boolean included
) {
}
