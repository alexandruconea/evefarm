package com.evefarm.service;

import com.evefarm.model.TrackerSnapshot;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SnapshotRuns {

    static final Duration RUN_GAP = Duration.ofMinutes(2);
    static final Duration RUN_MAX_LENGTH = Duration.ofMinutes(15);

    public static List<List<TrackerSnapshot>> of(List<TrackerSnapshot> snapshots) {
        List<TrackerSnapshot> sorted = snapshots.stream()
                .sorted(Comparator.comparing(TrackerSnapshot::capturedAt))
                .toList();
        List<List<TrackerSnapshot>> runs = new ArrayList<>();
        List<TrackerSnapshot> run = new ArrayList<>();
        for (TrackerSnapshot snapshot : sorted) {
            if (!run.isEmpty() && (snapshot.capturedAt().isAfter(run.getLast().capturedAt().plus(RUN_GAP))
                    || snapshot.capturedAt().isAfter(run.getFirst().capturedAt().plus(RUN_MAX_LENGTH)))) {
                runs.add(run);
                run = new ArrayList<>();
            }
            run.add(snapshot);
        }
        if (!run.isEmpty()) {
            runs.add(run);
        }
        return runs;
    }

    private SnapshotRuns() {
    }
}
