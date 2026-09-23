package com.evefarm.esi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AssetDto(
        @JsonProperty("item_id") long itemId,
        @JsonProperty("type_id") int typeId,
        @JsonProperty("quantity") long quantity,
        @JsonProperty("location_id") long locationId,
        @JsonProperty("location_type") String locationType,
        @JsonProperty("location_flag") String locationFlag,
        @JsonProperty("is_singleton") boolean isSingleton
) {
}
