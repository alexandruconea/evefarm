package com.evefarm.esi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BlueprintDto(
        @JsonProperty("item_id") long itemId,
        @JsonProperty("type_id") int typeId,
        @JsonProperty("location_id") Long locationId,
        @JsonProperty("location_flag") String locationFlag,
        @JsonProperty("quantity") int quantity,
        @JsonProperty("material_efficiency") int materialEfficiency,
        @JsonProperty("time_efficiency") int timeEfficiency,
        @JsonProperty("runs") int runs
) {
}
