package com.evefarm.esi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CharacterClonesDto(
        @JsonProperty("jump_clones") List<JumpClone> jumpClones
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JumpClone(
            @JsonProperty("implants") List<Integer> implants
    ) {
    }
}
