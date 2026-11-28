package com.evefarm.esi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CorporationDivisionsDto(
        @JsonProperty("hangar") List<Division> hangar,
        @JsonProperty("wallet") List<Division> wallet
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Division(
            @JsonProperty("division") int division,
            @JsonProperty("name") String name
    ) {
    }
}
