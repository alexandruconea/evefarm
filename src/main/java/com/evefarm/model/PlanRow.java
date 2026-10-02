package com.evefarm.model;

import java.time.Duration;
import java.time.Instant;

public record PlanRow(
        int position,
        SkillPlanEntry entry,
        String skillName,
        String groupName,
        int rank,
        String primaryAttribute,
        String secondaryAttribute,
        long spNeeded,
        long spTotalAfter,
        double percentDone,
        Duration time,
        Instant start,
        Instant finish
) {
}
