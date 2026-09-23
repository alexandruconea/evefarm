package com.evefarm.esi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AssetNameDto(
        @JsonProperty("item_id") long itemId,
        @JsonProperty("name") String name
) {
}
