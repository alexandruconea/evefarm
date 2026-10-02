package com.evefarm.model;

import java.util.List;

public record SkillInfo(
        int skillId,
        String name,
        String groupName,
        String description,
        int rank,
        String primaryAttribute,
        String secondaryAttribute,
        List<SkillRequirement> requirements
) {
}
