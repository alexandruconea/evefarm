package com.evefarm.model;

public record SkillPlanEntry(int skillId, int level, boolean planned, String notes) {

    public SkillPlanEntry withPlanned(boolean value) {
        return new SkillPlanEntry(skillId, level, value, notes);
    }

    public SkillPlanEntry withNotes(String value) {
        return new SkillPlanEntry(skillId, level, planned, value);
    }
}
