package com.evefarm.esi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CorporationWalletDto(
        @JsonProperty("division") int division,
        @JsonProperty("balance") double balance
) {
}
