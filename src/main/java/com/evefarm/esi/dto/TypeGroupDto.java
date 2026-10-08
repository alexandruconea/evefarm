package com.evefarm.esi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TypeGroupDto(
        @JsonProperty("name") String name,
        @JsonProperty("published") Boolean published,
        @JsonProperty("types") List<Integer> types
) {
}
