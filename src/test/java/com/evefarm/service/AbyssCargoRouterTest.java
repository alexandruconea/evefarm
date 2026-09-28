package com.evefarm.service;

import com.evefarm.model.AbyssalRun;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static com.evefarm.service.AbyssTrackerService.Phase.IN_ABYSS;
import static com.evefarm.service.AbyssTrackerService.Phase.STOPPED;
import static com.evefarm.service.AbyssTrackerService.Phase.WAITING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class AbyssCargoRouterTest {

    private static final Instant NOW = Instant.parse("2026-09-28T18:00:00Z");
    private static final AbyssalRun RUN = new AbyssalRun(7, 1, "Pilot", NOW, 900, null, null, null, null, true, 0,
            null, null);

    private final AbyssCargoRouter router = new AbyssCargoRouter();

    @Test
    void aCopyWhileWaitingIsTheCargoBeforeTheNextRun() {
        assertEquals(new AbyssCargoRouter.Before("cargo"), router.copied(WAITING, "cargo", NOW));
    }

    @Test
    void theFirstCopyAfterARunIsItsCargoAfterAndTheNextOneTheCargoBefore() {
        assertEquals(Optional.empty(), router.runFinished(RUN, NOW));

        assertEquals(new AbyssCargoRouter.After(RUN, "after"), router.copied(WAITING, "after", NOW.plusSeconds(20)));
        assertEquals(new AbyssCargoRouter.Before("unloaded"), router.copied(WAITING, "unloaded", NOW.plusSeconds(90)));
    }

    @Test
    void aCopyMadeJustBeforeTheExitWasNoticedIsTheCargoAfter() {
        assertInstanceOf(AbyssCargoRouter.Held.class, router.copied(IN_ABYSS, "early", NOW));
        assertInstanceOf(AbyssCargoRouter.Held.class, router.copied(IN_ABYSS, "final", NOW.plusSeconds(30)));

        assertEquals(Optional.of(new AbyssCargoRouter.After(RUN, "final")), router.runFinished(RUN, NOW.plusSeconds(45)));
        assertEquals(new AbyssCargoRouter.Before("next"), router.copied(WAITING, "next", NOW.plusSeconds(60)),
                "the copy made in the Abyss was the cargo after, so the next one is the cargo before");
    }

    @Test
    void anOldCopyFromInsideTheAbyssIsNotTheCargoAfter() {
        router.copied(IN_ABYSS, "mid-run", NOW);

        assertEquals(Optional.empty(),
                router.runFinished(RUN, NOW.plus(AbyssCargoRouter.HOLD_LIMIT).plusSeconds(1)));
        assertEquals(new AbyssCargoRouter.After(RUN, "after"),
                router.copied(WAITING, "after", NOW.plus(AbyssCargoRouter.HOLD_LIMIT).plusSeconds(20)));
    }

    @Test
    void copiesAreIgnoredWhenNotTrackingAndAResetForgetsTheWaitingRun() {
        assertInstanceOf(AbyssCargoRouter.Ignored.class, router.copied(STOPPED, "cargo", NOW));
        assertInstanceOf(AbyssCargoRouter.Ignored.class, router.copied(null, "cargo", NOW));

        router.runFinished(RUN, NOW);
        router.reset();

        assertEquals(new AbyssCargoRouter.Before("cargo"), router.copied(WAITING, "cargo", NOW));
    }
}
