package com.evefarm.esi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StructureDto(
        @JsonProperty("name") String name,
        @JsonProperty("solar_system_id") long solarSystemId
) {
}
