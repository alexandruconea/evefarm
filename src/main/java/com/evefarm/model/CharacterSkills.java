package com.evefarm.model;

import java.util.List;

public record CharacterSkills(
        long characterId,
        List<OwnedSkill> skills,
        CharacterAttributes attributes,
        List<Integer> implants,
        long totalSp,
        long unallocatedSp,
        Integer bonusRemaps,
        String lastRemapDate,
        String remapCooldownDate,
        String fetchedAt
) {
}
