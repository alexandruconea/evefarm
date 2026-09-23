package com.evefarm.esi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StationDto(
        @JsonProperty("station_id") long stationId,
        @JsonProperty("name") String name,
        @JsonProperty("system_id") long systemId
) {
}
