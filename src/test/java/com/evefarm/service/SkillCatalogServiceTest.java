package com.evefarm.service;

import com.evefarm.esi.dto.TypeDetailsDto;
import com.evefarm.model.CharacterAttributes;
import com.evefarm.model.SkillInfo;
import com.evefarm.model.SkillRequirement;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkillCatalogServiceTest {

    private static TypeDetailsDto.DogmaAttribute attribute(int id, double value) {
        return new TypeDetailsDto.DogmaAttribute(id, value);
    }

    @Test
    void aSkillIsReadFromItsDogmaAttributes() {
        TypeDetailsDto type = new TypeDetailsDto(3304, "Medium Hybrid Turret",
                "Operation of <b>medium</b> hybrid turrets.<br>5% bonus.", 255, true, List.of(
                attribute(275, 3), attribute(180, 167), attribute(181, 168),
                attribute(182, 3301), attribute(277, 3), attribute(183, 3300), attribute(278, 2)));

        SkillInfo skill = SkillCatalogService.toSkill(type, "Gunnery");

        assertEquals(3, skill.rank());
        assertEquals(CharacterAttributes.PERCEPTION, skill.primaryAttribute());
        assertEquals(CharacterAttributes.WILLPOWER, skill.secondaryAttribute());
        assertEquals(List.of(new SkillRequirement(3301, 3), new SkillRequirement(3300, 2)), skill.requirements());
        assertEquals("Operation of medium hybrid turrets.\n5% bonus.", skill.description());
    }

    @Test
    void anImplantGivesItsAttributeBonuses() {
        assertEquals(new CharacterAttributes(0, 4, 0, 0, 0),
                SkillCatalogService.bonusOf(Map.of(176, 4.0, 1083, 0.0)));
    }
}
