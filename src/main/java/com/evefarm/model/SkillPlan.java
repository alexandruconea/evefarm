package com.evefarm.model;

public record SkillPlan(long planId, long characterId, String name) {

    @Override
    public String toString() {
        return name;
    }
}
