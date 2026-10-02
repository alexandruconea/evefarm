package com.evefarm.esi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CharacterSkillsDto(
        @JsonProperty("total_sp") long totalSp,
        @JsonProperty("unallocated_sp") Long unallocatedSp,
        @JsonProperty("skills") List<Skill> skills
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Skill(
            @JsonProperty("skill_id") int skillId,
            @JsonProperty("skillpoints_in_skill") long skillPoints,
            @JsonProperty("trained_skill_level") int trainedLevel,
            @JsonProperty("active_skill_level") int activeLevel
    ) {
    }
}
