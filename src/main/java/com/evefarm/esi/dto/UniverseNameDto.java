package com.evefarm.esi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UniverseNameDto(
        @JsonProperty("id") long id,
        @JsonProperty("name") String name,
        @JsonProperty("category") String category
) {
}
