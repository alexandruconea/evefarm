package com.evefarm.esi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CharacterAttributesDto(
        @JsonProperty("charisma") int charisma,
        @JsonProperty("intelligence") int intelligence,
        @JsonProperty("memory") int memory,
        @JsonProperty("perception") int perception,
        @JsonProperty("willpower") int willpower,
        @JsonProperty("bonus_remaps") Integer bonusRemaps,
        @JsonProperty("last_remap_date") String lastRemapDate,
        @JsonProperty("accrued_remap_cooldown_date") String remapCooldownDate
) {
}
