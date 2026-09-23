package com.evefarm.esi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SkillsDto(
        @JsonProperty("total_sp") long totalSp,
        @JsonProperty("skills") List<SkillEntry> skills
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SkillEntry(
            @JsonProperty("skill_id") int skillId,
            @JsonProperty("trained_skill_level") int trainedSkillLevel,
            @JsonProperty("skillpoints_in_skill") long skillpointsInSkill
    ) {
    }
}
