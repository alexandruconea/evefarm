package com.evefarm.service;

import com.evefarm.esi.dto.SkillQueueDto;
import com.evefarm.model.SkillRequirement;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkillServiceTest {

    @Test
    void theQueueKeepsItsOrderAndDropsFinishedLevels() {
        Instant now = Instant.parse("2026-10-01T12:00:00Z");
        List<SkillQueueDto> queue = List.of(
                new SkillQueueDto(3301, 3, 2, "2026-10-03T00:00:00Z"),
                new SkillQueueDto(3300, 4, 0, "2026-10-01T08:00:00Z"),
                new SkillQueueDto(3300, 5, 1, "2026-10-02T00:00:00Z"),
                new SkillQueueDto(3449, 1, 3, null));

        assertEquals(List.of(new SkillRequirement(3300, 5), new SkillRequirement(3301, 3),
                new SkillRequirement(3449, 1)), SkillService.queuedLevels(queue, now));
    }
}
