package com.evefarm.esi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CorporationInfoDto(
        @JsonProperty("name") String name,
        @JsonProperty("ticker") String ticker,
        @JsonProperty("ceo_id") Long ceoId
) {
}
