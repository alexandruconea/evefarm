package com.evefarm.esi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UniverseTypeDto(
        @JsonProperty("type_id") int typeId,
        @JsonProperty("name") String name,
        @JsonProperty("group_id") int groupId,
        @JsonProperty("volume") Double volume
) {
}
