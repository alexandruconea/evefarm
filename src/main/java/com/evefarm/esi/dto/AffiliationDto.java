package com.evefarm.esi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AffiliationDto(
        @JsonProperty("character_id") long characterId,
        @JsonProperty("corporation_id") long corporationId,
        @JsonProperty("alliance_id") Long allianceId
) {
}
