package com.evefarm.service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

final class AbyssRoomWatcher {

    static final Duration ROOM_GAP = Duration.ofSeconds(25);
    static final Duration SPAWN_WINDOW = Duration.ofSeconds(6);
    static final Duration NEW_RUN_GAP = Duration.ofMinutes(3);

    private final Set<String> names = new LinkedHashSet<>();
    private Instant lastSeen;
    private Instant pendingSince;
    private AbyssSpawnCatalog.RoomReport spoken;
    private int room;

    void observe(String npcName, Instant at) {
        if (AbyssSpawnCatalog.identify(npcName).isEmpty()) {
            return;
        }
        if (lastSeen == null || Duration.between(lastSeen, at).compareTo(ROOM_GAP) > 0) {
            if (lastSeen == null || Duration.between(lastSeen, at).compareTo(NEW_RUN_GAP) > 0) {
                room = 0;
            }
            names.clear();
            spoken = null;
            pendingSince = at;
        }
        lastSeen = at;
        if (names.add(npcName.strip()) && spoken != null && pendingSince == null
                && AbyssSpawnCatalog.report(room, names).since(spoken).hasNews()) {
            pendingSince = at;
        }
    }

    Optional<AbyssSpawnCatalog.RoomReport> poll(Instant now) {
        if (pendingSince == null || Duration.between(pendingSince, now).compareTo(SPAWN_WINDOW) < 0) {
            return Optional.empty();
        }
        pendingSince = null;
        if (spoken == null) {
            room++;
            spoken = AbyssSpawnCatalog.report(room, names);
            return Optional.of(spoken);
        }
        AbyssSpawnCatalog.RoomReport current = AbyssSpawnCatalog.report(room, names);
        AbyssSpawnCatalog.RoomReport news = current.since(spoken);
        spoken = current;
        return news.hasNews() ? Optional.of(news) : Optional.empty();
    }

    void reset() {
        names.clear();
        lastSeen = null;
        pendingSince = null;
        spoken = null;
        room = 0;
    }
}
