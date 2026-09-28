package com.evefarm.service;

import com.evefarm.model.AbyssalRun;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public final class AbyssCargoRouter {

    static final Duration HOLD_LIMIT = Duration.ofMinutes(2);

    public sealed interface Route permits Before, After, Held, Ignored {
    }

    public record Before(String cargo) implements Route {
    }

    public record After(AbyssalRun run, String cargo) implements Route {
    }

    public record Held() implements Route {
    }

    public record Ignored() implements Route {
    }

    private AbyssalRun awaiting;
    private String held;
    private Instant heldAt;

    public synchronized Route copied(AbyssTrackerService.Phase phase, String cargo, Instant at) {
        if (phase == null || phase == AbyssTrackerService.Phase.STOPPED) {
            return new Ignored();
        }
        if (phase == AbyssTrackerService.Phase.IN_ABYSS) {
            held = cargo;
            heldAt = at;
            return new Held();
        }
        if (awaiting != null) {
            After after = new After(awaiting, cargo);
            awaiting = null;
            return after;
        }
        return new Before(cargo);
    }

    public synchronized Optional<After> runFinished(AbyssalRun run, Instant at) {
        String copy = held;
        Instant copiedAt = heldAt;
        held = null;
        heldAt = null;
        if (copy != null && Duration.between(copiedAt, at).compareTo(HOLD_LIMIT) <= 0) {
            awaiting = null;
            return Optional.of(new After(run, copy));
        }
        awaiting = run;
        return Optional.empty();
    }

    public synchronized void reset() {
        awaiting = null;
        held = null;
        heldAt = null;
    }
}
