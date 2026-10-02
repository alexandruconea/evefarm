package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.model.CharacterAttributes;
import com.evefarm.model.CharacterSkills;
import com.evefarm.model.OwnedSkill;
import com.evefarm.model.SkillInfo;
import com.evefarm.model.SkillPlan;
import com.evefarm.model.SkillPlanEntry;
import com.evefarm.model.SkillRequirement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillPlanDaoTest {

    private static final long PILOT = 90_000_001L;

    private Database database;
    private SkillPlanDao plans;

    @BeforeEach
    void setUp() {
        database = new Database(":memory:");
        MigrationRunner.run(database);
        new CharacterDao(database).upsert(PILOT, "Pilot", null, List.of(), "owner");
        plans = new SkillPlanDao(database);
    }

    @Test
    void aPlanKeepsItsSkillsInOrder() {
        SkillPlan plan = plans.create(PILOT, "Cruisers");
        List<SkillPlanEntry> entries = List.of(new SkillPlanEntry(3300, 1, false, null),
                new SkillPlanEntry(3301, 1, true, "first gun"), new SkillPlanEntry(3300, 2, true, null));

        plans.replaceEntries(plan.planId(), entries);

        assertEquals(entries, plans.entries(plan.planId()));
        assertEquals(List.of("Cruisers"), plans.listPlans(PILOT).stream().map(SkillPlan::name).toList());
    }

    @Test
    void twoPlansOfOneCharacterCannotShareAName() {
        plans.create(PILOT, "Cruisers");

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> plans.create(PILOT, "Cruisers"));
        assertTrue(error.getMessage().contains("already exists"));
    }

    @Test
    void deletingAPlanDeletesItsSkills() {
        SkillPlan plan = plans.create(PILOT, "Cruisers");
        plans.replaceEntries(plan.planId(), List.of(new SkillPlanEntry(3300, 1, true, null)));

        plans.delete(plan.planId());

        assertEquals(List.of(), plans.listPlans(PILOT));
        assertEquals(List.of(), plans.entries(plan.planId()));
    }

    @Test
    void theSkillCatalogKeepsRequirementsInOrder() {
        SkillCatalogDao catalog = new SkillCatalogDao(database);
        SkillInfo medium = new SkillInfo(3304, "Medium Hybrid Turret", "Gunnery", "text", 3,
                CharacterAttributes.PERCEPTION, CharacterAttributes.WILLPOWER,
                List.of(new SkillRequirement(3301, 3), new SkillRequirement(3300, 2)));

        catalog.replaceAll(List.of(medium));

        assertEquals(Map.of(3304, medium), catalog.loadAll());
    }

    @Test
    void aCharactersSkillsAndAttributesAreSaved() {
        CharacterSkillDao dao = new CharacterSkillDao(database);
        CharacterSkills skills = new CharacterSkills(PILOT, List.of(new OwnedSkill(3300, 8000, 3, 3)),
                new CharacterAttributes(17, 27, 21, 17, 17), List.of(9899, 9941), 5_000_000, 1_200, 1,
                "2026-01-01T00:00:00Z", "2027-01-01T00:00:00Z", "2026-10-01T10:00:00Z");

        dao.save(skills);

        assertEquals(skills, dao.find(PILOT).orElseThrow());
    }
}
