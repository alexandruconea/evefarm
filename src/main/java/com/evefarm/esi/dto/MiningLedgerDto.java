package com.evefarm.esi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MiningLedgerDto(
        @JsonProperty("date") String date,
        @JsonProperty("solar_system_id") long solarSystemId,
        @JsonProperty("type_id") int typeId,
        @JsonProperty("quantity") long quantity
) {
}
