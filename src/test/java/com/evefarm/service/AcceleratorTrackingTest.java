package com.evefarm.service;

import com.evefarm.esi.dto.TypeDetailsDto;
import com.evefarm.model.Accelerator;
import com.evefarm.model.CharacterAccelerator;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcceleratorTrackingTest {

    private static final Instant NOW = Instant.parse("2026-10-01T20:00:00Z");
    private static final Accelerator GENIUS = new Accelerator(77919, "Genius 'Boost' Cerebral Accelerator", 12, 288);
    private static final List<Accelerator> CATALOG = List.of(
            new Accelerator(47000, "Old Cerebral Accelerator", 12, 24), GENIUS,
            new Accelerator(60000, "Standard Cerebral Accelerator", 3, 24));

    @Test
    void theNewestAcceleratorWithTheBonusIsTheLikelyOne() {
        assertEquals(Optional.of(GENIUS), AcceleratorTracking.likelyAccelerator(CATALOG, 12));
        assertEquals(Optional.empty(), AcceleratorTracking.likelyAccelerator(CATALOG, 7));
    }

    @Test
    void biologyMakesItLast() {
        assertEquals(Duration.ofHours(288), AcceleratorTracking.duration(GENIUS, 0));
        assertEquals(Duration.ofHours(576), AcceleratorTracking.duration(GENIUS, 5));
    }

    @Test
    void aNewAcceleratorEndsItsDurationAfterItWasFirstSeen() {
        CharacterAccelerator tracked = AcceleratorTracking.track(null, 7, 12, GENIUS, 5, NOW);

        assertEquals(NOW, tracked.firstSeen());
        assertEquals(NOW.plus(Duration.ofHours(576)), tracked.endsAt());
        assertEquals("Genius 'Boost' Cerebral Accelerator", tracked.name());
        assertFalse(tracked.setByUser());
    }

    @Test
    void theSameAcceleratorKeepsItsEndUntilItIsOver() {
        CharacterAccelerator seen = AcceleratorTracking.track(null, 7, 12, GENIUS, 5, NOW).withEnd(
                NOW.plus(Duration.ofDays(3)), true);

        assertSame(seen, AcceleratorTracking.track(seen, 7, 12, GENIUS, 5, NOW.plus(Duration.ofDays(1))));
        CharacterAccelerator again = AcceleratorTracking.track(seen, 7, 12, GENIUS, 5, NOW.plus(Duration.ofDays(4)));
        assertEquals(NOW.plus(Duration.ofDays(4)), again.firstSeen(), "still boosted after it ended: a new one");
        assertNull(AcceleratorTracking.track(seen, 7, 0, GENIUS, 5, NOW), "the bonus is gone");
    }

    @Test
    void anUnknownAcceleratorHasNoEndUntilItIsKnown() {
        CharacterAccelerator unknown = AcceleratorTracking.track(null, 7, 12, null, 5, NOW);
        assertNull(unknown.endsAt());

        CharacterAccelerator known = AcceleratorTracking.track(unknown, 7, 12, GENIUS, 5, NOW.plusSeconds(60));
        assertEquals(NOW.plus(Duration.ofHours(576)), known.endsAt(), "counted from when it was first seen");
    }

    @Test
    void theTimeLeftCanBeTypedTheWayEveShowsIt() {
        assertEquals(NOW.plus(Duration.ofHours(505).plusMinutes(31).plusSeconds(31)),
                AcceleratorTracking.parseEnd("505:31:31", NOW, ZoneOffset.UTC));
        assertEquals(NOW.plus(Duration.ofHours(2).plusMinutes(5)),
                AcceleratorTracking.parseEnd(" 2:05 ", NOW, ZoneOffset.UTC));
        assertNull(AcceleratorTracking.parseEnd("505:31:31:00", NOW, ZoneOffset.UTC));
        assertNull(AcceleratorTracking.parseEnd("99999999999999999999h", NOW, ZoneOffset.UTC));
        assertNull(AcceleratorTracking.parseEnd("999999999999999:00", NOW, ZoneOffset.UTC));
    }

    @Test
    void theEndIsTypedAsTimeLeftOrAsADate() {
        assertEquals(NOW.plus(Duration.ofDays(6).plusHours(4)),
                AcceleratorTracking.parseEnd("6d 4h", NOW, ZoneOffset.UTC));
        assertEquals(NOW.plus(Duration.ofHours(2).plusMinutes(30)),
                AcceleratorTracking.parseEnd("2H30m", NOW, ZoneOffset.UTC));
        assertEquals(Instant.parse("2026-10-25T18:00:00Z"),
                AcceleratorTracking.parseEnd("2026-10-25 18:00", NOW, ZoneOffset.UTC));
        assertEquals(Instant.parse("2026-10-25T00:00:00Z"),
                AcceleratorTracking.parseEnd("2026-10-25", NOW, ZoneOffset.UTC));
        assertNull(AcceleratorTracking.parseEnd("soon", NOW, ZoneOffset.UTC));
        assertNull(AcceleratorTracking.parseEnd("", NOW, ZoneOffset.UTC));
    }

    @Test
    void anAcceleratorIsReadFromItsDogmaAttributes() {
        List<TypeDetailsDto.DogmaAttribute> attributes = List.of(
                new TypeDetailsDto.DogmaAttribute(175, 12), new TypeDetailsDto.DogmaAttribute(176, 12),
                new TypeDetailsDto.DogmaAttribute(177, 12), new TypeDetailsDto.DogmaAttribute(178, 12),
                new TypeDetailsDto.DogmaAttribute(179, 12), new TypeDetailsDto.DogmaAttribute(330, 1_036_800_000));

        assertEquals(Optional.of(GENIUS), SkillCatalogService.toAccelerator(new TypeDetailsDto(77919,
                "Genius 'Boost' Cerebral Accelerator", "", true, attributes)));
        assertTrue(SkillCatalogService.isUsableAccelerator("Genius 'Boost' Cerebral Accelerator"));
        assertFalse(SkillCatalogService.isUsableAccelerator("Expired Cerebral Accelerator"));
        assertFalse(SkillCatalogService.isUsableAccelerator("Advanced gunnery Skill accelerator"));
    }
}
