package com.evefarm.esi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UniverseGroupDto(
        @JsonProperty("group_id") int groupId,
        @JsonProperty("name") String name,
        @JsonProperty("category_id") int categoryId
) {
}
