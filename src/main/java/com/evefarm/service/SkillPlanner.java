package com.evefarm.service;

import com.evefarm.model.CharacterAttributes;
import com.evefarm.model.OwnedSkill;
import com.evefarm.model.PlanRow;
import com.evefarm.model.SkillInfo;
import com.evefarm.model.SkillPlanEntry;
import com.evefarm.model.SkillRequirement;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SkillPlanner {

    public static final int MAX_LEVEL = 5;
    public static final int BASE_ATTRIBUTE_POINTS = 99;
    private static final long[] LEVEL_SP = {0, 250, 1415, 8000, 45255, 256000};

    public static long spForLevel(int rank, int level) {
        return rank * LEVEL_SP[Math.max(0, Math.min(MAX_LEVEL, level))];
    }

    public static double spPerMinute(SkillInfo skill, CharacterAttributes attributes) {
        return attributes.get(skill.primaryAttribute()) + attributes.get(skill.secondaryAttribute()) / 2.0;
    }

    public static int acceleratorBonus(CharacterAttributes fromEve, CharacterAttributes implants) {
        int extra = fromEve.minus(implants).total() - BASE_ATTRIBUTE_POINTS;
        int attributes = CharacterAttributes.NAMES.size();
        return extra > 0 && extra % attributes == 0 ? extra / attributes : 0;
    }

    public static List<SkillPlanEntry> withoutTrained(List<SkillPlanEntry> entries, Map<Integer, Integer> trained) {
        return entries.stream().filter(entry -> entry.level() > trained.getOrDefault(entry.skillId(), 0)).toList();
    }

    public static List<SkillPlanEntry> add(List<SkillPlanEntry> entries, Map<Integer, SkillInfo> catalog,
                                           Map<Integer, Integer> trained, int skillId, int level) {
        List<SkillPlanEntry> plan = new ArrayList<>(entries);
        addLevels(plan, catalog, trained, skillId, Math.min(MAX_LEVEL, level), true, new HashSet<>());
        return plan;
    }

    private static void addLevels(List<SkillPlanEntry> plan, Map<Integer, SkillInfo> catalog,
                                  Map<Integer, Integer> trained, int skillId, int level, boolean planned,
                                  Set<Integer> path) {
        SkillInfo skill = catalog.get(skillId);
        if (skill == null) {
            throw new IllegalArgumentException("Unknown skill " + skillId);
        }
        if (!path.add(skillId)) {
            return;
        }
        for (SkillRequirement requirement : skill.requirements()) {
            addLevels(plan, catalog, trained, requirement.skillId(), requirement.level(), false, path);
        }
        path.remove(skillId);
        for (int next = trained.getOrDefault(skillId, 0) + 1; next <= level; next++) {
            int index = indexOf(plan, skillId, next);
            boolean target = planned && next == level;
            if (index < 0) {
                plan.add(new SkillPlanEntry(skillId, next, target, null));
            } else if (target && !plan.get(index).planned()) {
                plan.set(index, plan.get(index).withPlanned(true));
            }
        }
    }

    public static List<SkillPlanEntry> remove(List<SkillPlanEntry> entries, Map<Integer, SkillInfo> catalog,
                                              Map<Integer, Integer> trained, int index) {
        List<SkillPlanEntry> plan = new ArrayList<>(entries);
        SkillPlanEntry removed = plan.get(index);
        plan.removeIf(entry -> entry.skillId() == removed.skillId() && entry.level() >= removed.level());
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i < plan.size(); i++) {
                if (missesPrerequisite(plan, i, catalog, trained)) {
                    SkillPlanEntry broken = plan.get(i);
                    plan.removeIf(entry -> entry.skillId() == broken.skillId() && entry.level() >= broken.level());
                    changed = true;
                    break;
                }
            }
        }
        changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i < plan.size(); i++) {
                if (!plan.get(i).planned() && !isNeeded(plan, i, catalog)) {
                    plan.remove(i);
                    changed = true;
                    break;
                }
            }
        }
        return plan;
    }

    public static List<SkillPlanEntry> move(List<SkillPlanEntry> entries, Map<Integer, SkillInfo> catalog,
                                            Map<Integer, Integer> trained, int from, int to) {
        if (from < 0 || from >= entries.size() || to < 0 || to >= entries.size() || from == to) {
            return entries;
        }
        List<SkillPlanEntry> plan = new ArrayList<>(entries);
        plan.add(to, plan.remove(from));
        return isValid(plan, catalog, trained) ? plan : entries;
    }

    public static boolean isValid(List<SkillPlanEntry> plan, Map<Integer, SkillInfo> catalog,
                                  Map<Integer, Integer> trained) {
        for (int i = 0; i < plan.size(); i++) {
            if (missesPrerequisite(plan, i, catalog, trained)) {
                return false;
            }
        }
        return true;
    }

    private static boolean missesPrerequisite(List<SkillPlanEntry> plan, int position,
                                              Map<Integer, SkillInfo> catalog, Map<Integer, Integer> trained) {
        SkillPlanEntry entry = plan.get(position);
        if (entry.level() > trained.getOrDefault(entry.skillId(), 0) + 1
                && !isBefore(plan, entry.skillId(), entry.level() - 1, position)) {
            return true;
        }
        SkillInfo skill = catalog.get(entry.skillId());
        if (skill == null) {
            return false;
        }
        for (SkillRequirement requirement : skill.requirements()) {
            if (trained.getOrDefault(requirement.skillId(), 0) < requirement.level()
                    && !isBefore(plan, requirement.skillId(), requirement.level(), position)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNeeded(List<SkillPlanEntry> plan, int position, Map<Integer, SkillInfo> catalog) {
        SkillPlanEntry entry = plan.get(position);
        for (SkillPlanEntry other : plan) {
            if (other.skillId() == entry.skillId() && other.level() > entry.level()) {
                return true;
            }
            SkillInfo skill = catalog.get(other.skillId());
            if (skill != null && skill.requirements().stream().anyMatch(requirement ->
                    requirement.skillId() == entry.skillId() && requirement.level() >= entry.level())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBefore(List<SkillPlanEntry> plan, int skillId, int level, int position) {
        int index = indexOf(plan, skillId, level);
        return index >= 0 && index < position;
    }

    static int indexOf(List<SkillPlanEntry> plan, int skillId, int level) {
        for (int i = 0; i < plan.size(); i++) {
            if (plan.get(i).skillId() == skillId && plan.get(i).level() == level) {
                return i;
            }
        }
        return -1;
    }

    public static Duration trainingTime(long sp, double boostedRate, double baseRate, Instant start,
                                        Instant boostEnds) {
        if (sp <= 0) {
            return Duration.ZERO;
        }
        if (boostEnds == null || !start.isBefore(boostEnds) || boostedRate <= baseRate) {
            double rate = boostEnds == null ? boostedRate : baseRate;
            return rate > 0 ? Duration.ofSeconds(Math.round(sp / rate * 60)) : Duration.ZERO;
        }
        double boostedMinutes = Duration.between(start, boostEnds).toSeconds() / 60.0;
        double neededAtBoost = sp / boostedRate;
        if (neededAtBoost <= boostedMinutes) {
            return Duration.ofSeconds(Math.round(neededAtBoost * 60));
        }
        double rest = sp - boostedMinutes * boostedRate;
        double minutes = boostedMinutes + (baseRate > 0 ? rest / baseRate : 0);
        return Duration.ofSeconds(Math.round(minutes * 60));
    }

    public static List<PlanRow> rows(List<SkillPlanEntry> entries, Map<Integer, SkillInfo> catalog,
                                     Map<Integer, OwnedSkill> owned, CharacterAttributes attributes,
                                     int acceleratorBonus, Instant acceleratorEnds, long totalSp, Instant start) {
        CharacterAttributes base = acceleratorBonus > 0
                ? attributes.minus(CharacterAttributes.all(acceleratorBonus)) : attributes;
        Instant boostEnds = acceleratorBonus > 0 ? acceleratorEnds : start;
        List<PlanRow> rows = new ArrayList<>();
        Instant time = start;
        long spTotal = totalSp;
        for (int i = 0; i < entries.size(); i++) {
            SkillPlanEntry entry = entries.get(i);
            SkillInfo skill = catalog.get(entry.skillId());
            if (skill == null) {
                rows.add(new PlanRow(i + 1, entry, "Skill #" + entry.skillId(), "", 0, "", "", 0, spTotal, 0,
                        Duration.ZERO, time, time));
                continue;
            }
            long levelStart = spForLevel(skill.rank(), entry.level() - 1);
            long levelEnd = spForLevel(skill.rank(), entry.level());
            long current = levelStart;
            OwnedSkill ownedSkill = owned.get(entry.skillId());
            if (ownedSkill != null && ownedSkill.trainedLevel() == entry.level() - 1) {
                current = Math.max(levelStart, Math.min(ownedSkill.skillPoints(), levelEnd));
            }
            long needed = levelEnd - current;
            double percent = levelEnd > levelStart ? (double) (current - levelStart) / (levelEnd - levelStart) : 1;
            Duration duration = trainingTime(needed, spPerMinute(skill, attributes), spPerMinute(skill, base), time,
                    boostEnds);
            spTotal += needed;
            rows.add(new PlanRow(i + 1, entry, skill.name(), skill.groupName(), skill.rank(),
                    skill.primaryAttribute(), skill.secondaryAttribute(), needed, spTotal, percent, duration, time,
                    time.plus(duration)));
            time = time.plus(duration);
        }
        return rows;
    }

    private SkillPlanner() {
    }
}
