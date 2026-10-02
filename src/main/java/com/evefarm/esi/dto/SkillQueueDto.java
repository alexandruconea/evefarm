package com.evefarm.esi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SkillQueueDto(
        @JsonProperty("skill_id") int skillId,
        @JsonProperty("finished_level") int finishedLevel,
        @JsonProperty("queue_position") int queuePosition,
        @JsonProperty("finish_date") String finishDate
) {
}
