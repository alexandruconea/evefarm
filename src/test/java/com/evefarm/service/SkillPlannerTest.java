package com.evefarm.service;

import com.evefarm.model.CharacterAttributes;
import com.evefarm.model.OwnedSkill;
import com.evefarm.model.PlanRow;
import com.evefarm.model.SkillInfo;
import com.evefarm.model.SkillPlanEntry;
import com.evefarm.model.SkillRequirement;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class SkillPlannerTest {

    private static final int GUNNERY = 3300;
    private static final int SMALL_HYBRID = 3301;
    private static final int MEDIUM_HYBRID = 3304;
    private static final int NAVIGATION = 3449;
    private static final Map<Integer, SkillInfo> CATALOG = Map.of(
            GUNNERY, skill(GUNNERY, "Gunnery", 1),
            SMALL_HYBRID, skill(SMALL_HYBRID, "Small Hybrid Turret", 1, new SkillRequirement(GUNNERY, 1)),
            MEDIUM_HYBRID, skill(MEDIUM_HYBRID, "Medium Hybrid Turret", 3, new SkillRequirement(SMALL_HYBRID, 3),
                    new SkillRequirement(GUNNERY, 1)),
            NAVIGATION, skill(NAVIGATION, "Navigation", 1));
    private static final CharacterAttributes ATTRIBUTES = new CharacterAttributes(17, 17, 17, 20, 20);

    private static SkillInfo skill(int id, String name, int rank, SkillRequirement... requirements) {
        return new SkillInfo(id, name, "Gunnery", "", rank, CharacterAttributes.PERCEPTION,
                CharacterAttributes.WILLPOWER, List.of(requirements));
    }

    private static SkillPlanEntry entry(int skillId, int level, boolean planned) {
        return new SkillPlanEntry(skillId, level, planned, null);
    }

    @Test
    void skillPointsFollowTheRank() {
        assertEquals(250, SkillPlanner.spForLevel(1, 1));
        assertEquals(45_255, SkillPlanner.spForLevel(1, 4));
        assertEquals(768_000, SkillPlanner.spForLevel(3, 5));
        assertEquals(0, SkillPlanner.spForLevel(3, 0));
    }

    @Test
    void addingASkillAddsItsPrerequisitesFirst() {
        List<SkillPlanEntry> plan = SkillPlanner.add(List.of(), CATALOG, Map.of(), MEDIUM_HYBRID, 2);

        assertEquals(List.of(entry(GUNNERY, 1, false), entry(SMALL_HYBRID, 1, false), entry(SMALL_HYBRID, 2, false),
                entry(SMALL_HYBRID, 3, false), entry(MEDIUM_HYBRID, 1, false), entry(MEDIUM_HYBRID, 2, true)), plan);
    }

    @Test
    void trainedLevelsAreNotPlannedAgain() {
        List<SkillPlanEntry> plan = SkillPlanner.add(List.of(), CATALOG, Map.of(GUNNERY, 3, SMALL_HYBRID, 2),
                MEDIUM_HYBRID, 1);

        assertEquals(List.of(entry(SMALL_HYBRID, 3, false), entry(MEDIUM_HYBRID, 1, true)), plan);
    }

    @Test
    void planningAPrerequisiteMarksItPlanned() {
        List<SkillPlanEntry> plan = SkillPlanner.add(List.of(), CATALOG, Map.of(), SMALL_HYBRID, 2);
        plan = SkillPlanner.add(plan, CATALOG, Map.of(), GUNNERY, 1);

        assertEquals(entry(GUNNERY, 1, true), plan.getFirst());
        assertEquals(3, plan.size());
    }

    @Test
    void removingASkillTakesOffWhatNeedsItAndWhatOnlyItNeeded() {
        List<SkillPlanEntry> plan = SkillPlanner.add(List.of(), CATALOG, Map.of(), SMALL_HYBRID, 3);
        plan = SkillPlanner.add(plan, CATALOG, Map.of(), MEDIUM_HYBRID, 2);
        plan = SkillPlanner.add(plan, CATALOG, Map.of(), NAVIGATION, 1);

        List<SkillPlanEntry> withoutMedium = SkillPlanner.remove(plan, CATALOG, Map.of(),
                SkillPlanner.indexOf(plan, MEDIUM_HYBRID, 1));
        assertEquals(List.of(entry(GUNNERY, 1, false), entry(SMALL_HYBRID, 1, false), entry(SMALL_HYBRID, 2, false),
                entry(SMALL_HYBRID, 3, true), entry(NAVIGATION, 1, true)), withoutMedium);

        List<SkillPlanEntry> withoutGunnery = SkillPlanner.remove(plan, CATALOG, Map.of(), 0);
        assertEquals(List.of(entry(NAVIGATION, 1, true)), withoutGunnery);
    }

    @Test
    void aSkillCannotMoveAboveWhatItNeeds() {
        List<SkillPlanEntry> plan = SkillPlanner.add(List.of(), CATALOG, Map.of(), SMALL_HYBRID, 1);
        plan = SkillPlanner.add(plan, CATALOG, Map.of(), NAVIGATION, 1);

        assertSame(plan, SkillPlanner.move(plan, CATALOG, Map.of(), 1, 0));
        assertSame(plan, SkillPlanner.move(plan, CATALOG, Map.of(), 0, 1));
        assertEquals(List.of(entry(GUNNERY, 1, false), entry(NAVIGATION, 1, true), entry(SMALL_HYBRID, 1, true)),
                SkillPlanner.move(plan, CATALOG, Map.of(), 2, 1));
    }

    @Test
    void trainedLevelsLeaveThePlan() {
        List<SkillPlanEntry> plan = SkillPlanner.add(List.of(), CATALOG, Map.of(), SMALL_HYBRID, 2);

        assertEquals(List.of(entry(SMALL_HYBRID, 2, true)),
                SkillPlanner.withoutTrained(plan, Map.of(GUNNERY, 1, SMALL_HYBRID, 1)));
    }

    @Test
    void trainingTimeUsesTheAttributesAndWhatIsAlreadyTrained() {
        Instant start = Instant.parse("2026-10-01T10:00:00Z");
        List<SkillPlanEntry> plan = List.of(entry(GUNNERY, 1, true), entry(GUNNERY, 2, true));
        Map<Integer, OwnedSkill> owned = Map.of(GUNNERY, new OwnedSkill(GUNNERY, 100, 0, 0));

        List<PlanRow> rows = SkillPlanner.rows(plan, CATALOG, owned, ATTRIBUTES, 0, null, 1_000_000, start);

        assertEquals(150, rows.get(0).spNeeded());
        assertEquals(0.4, rows.get(0).percentDone(), 1e-9);
        assertEquals(Duration.ofSeconds(300), rows.get(0).time(), "150 SP at 30 SP a minute");
        assertEquals(1_165, rows.get(1).spNeeded());
        assertEquals(rows.get(0).finish(), rows.get(1).start());
        assertEquals(1_001_315, rows.get(1).spTotalAfter());
    }

    @Test
    void aLevelThatOutlastsTheAcceleratorFinishesAtTheNormalSpeed() {
        Instant start = Instant.parse("2026-10-01T10:00:00Z");
        Instant ends = start.plus(Duration.ofMinutes(100));

        assertEquals(Duration.ofMinutes(50), SkillPlanner.trainingTime(3_000, 60, 30, start, ends));
        assertEquals(Duration.ofMinutes(150), SkillPlanner.trainingTime(7_500, 60, 30, start, ends),
                "6,000 SP in the first 100 minutes, the other 1,500 SP at half the speed");
        assertEquals(Duration.ofMinutes(100), SkillPlanner.trainingTime(3_000, 60, 30, ends, ends));
        assertEquals(Duration.ofMinutes(50), SkillPlanner.trainingTime(3_000, 60, 30, start, null),
                "an accelerator without an end lasts the whole plan");
    }

    @Test
    void theAcceleratorEndIsUsedAcrossThePlan() {
        Instant start = Instant.parse("2026-10-01T10:00:00Z");
        CharacterAttributes boosted = new CharacterAttributes(29, 29, 29, 32, 32);
        List<SkillPlanEntry> plan = List.of(entry(NAVIGATION, 1, true), entry(NAVIGATION, 2, true));

        List<PlanRow> rows = SkillPlanner.rows(plan, CATALOG, Map.of(), boosted, 12, start.plusSeconds(313), 0,
                start);

        assertEquals(Duration.ofSeconds(313), rows.get(0).time(), "250 SP at 48 SP a minute");
        assertEquals(Duration.ofSeconds(2_330), rows.get(1).time(), "1,165 SP at 30 SP a minute");
    }

    @Test
    void anAcceleratorIsWhatIsLeftAboveTheBasePointsAndTheImplants() {
        CharacterAttributes implants = new CharacterAttributes(5, 5, 5, 5, 5);

        assertEquals(12, SkillPlanner.acceleratorBonus(new CharacterAttributes(36, 37, 37, 37, 37), implants));
        assertEquals(0, SkillPlanner.acceleratorBonus(new CharacterAttributes(24, 25, 25, 25, 25), implants));
        assertEquals(0, SkillPlanner.acceleratorBonus(new CharacterAttributes(19, 20, 20, 20, 20),
                CharacterAttributes.NONE));
    }
}
