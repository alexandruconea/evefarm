package com.evefarm.service;

import com.evefarm.model.CharacterAttributes;
import com.evefarm.model.SkillInfo;
import com.evefarm.model.SkillPlanEntry;
import com.evefarm.model.SkillRequirement;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillPlanTextTest {

    private static final Map<Integer, SkillInfo> CATALOG = Map.of(
            3300, skill(3300, "Gunnery"),
            3301, skill(3301, "Small Hybrid Turret"));

    private static SkillInfo skill(int id, String name) {
        return new SkillInfo(id, name, "Gunnery", "", 1, CharacterAttributes.PERCEPTION,
                CharacterAttributes.WILLPOWER, List.of());
    }

    @Test
    void skillListsAreReadWithArabicOrRomanLevels() {
        SkillPlanText.ParsedSkills parsed = SkillPlanText.parseSkills(
                "Gunnery 4\r\nsmall hybrid turret III\n\n  \nNonsense 3\nGunnery\n", CATALOG);

        assertEquals(List.of(new SkillRequirement(3300, 4), new SkillRequirement(3301, 3)), parsed.skills());
        assertEquals(List.of("Nonsense 3", "Gunnery"), parsed.unknownLines());
    }

    @Test
    void aPlanIsWrittenOneLevelPerLine() {
        String text = SkillPlanText.export(List.of(new SkillPlanEntry(3300, 1, false, null),
                new SkillPlanEntry(3301, 2, true, "note")), CATALOG);

        assertEquals("Gunnery 1\nSmall Hybrid Turret 2\n", text);
    }

    @Test
    void aFitGivesTheShipAndEveryModuleChargeAndDrone() {
        String fit = """
                [Merlin, Test fit]
                Damage Control II
                Magnetic Field Stabilizer II

                5MN Microwarpdrive II
                Warp Scrambler II /offline

                Light Neutron Blaster II, Caldari Navy Antimatter Charge S
                Light Neutron Blaster II, Caldari Navy Antimatter Charge S
                [Empty High slot]

                Hobgoblin II x5
                """;

        assertTrue(SkillPlanText.isFit(fit));
        assertFalse(SkillPlanText.isFit("Gunnery 4"));
        assertEquals(List.of("Merlin", "Damage Control II", "Magnetic Field Stabilizer II", "5MN Microwarpdrive II",
                "Warp Scrambler II", "Light Neutron Blaster II", "Caldari Navy Antimatter Charge S", "Hobgoblin II"),
                SkillPlanText.fitItemNames(fit));
    }

    @Test
    void levelsAreShownInRomanNumerals() {
        assertEquals("IV", SkillPlanText.roman(4));
        assertEquals(5, SkillPlanText.level("v"));
    }
}
