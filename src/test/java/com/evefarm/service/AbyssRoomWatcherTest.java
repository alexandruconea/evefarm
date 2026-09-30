package com.evefarm.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AbyssRoomWatcherTest {

    private static final Instant START = Instant.parse("2026-09-29T06:33:43Z");

    private final AbyssRoomWatcher watcher = new AbyssRoomWatcher();

    private static Instant at(long seconds) {
        return START.plusSeconds(seconds);
    }

    @Test
    void aRoomIsReportedAFewSecondsAfterTheFightStarts() {
        watcher.observe("Scylla Tyrannos", at(0));
        watcher.observe("Nozeu", at(1));
        watcher.observe("Karybdis Tyrannos", at(3));
        assertEquals(Optional.empty(), watcher.poll(at(5)));

        AbyssSpawnCatalog.RoomReport report = watcher.poll(at(6)).orElseThrow();

        assertEquals(1, report.room());
        assertEquals(List.of("Scylla Tyrannos", "Karybdis Tyrannos"), report.npcs());
        watcher.observe("Karybdis Tyrannos", at(20));
        watcher.observe("Scylla Tyrannos", at(40));
        assertEquals(Optional.empty(), watcher.poll(at(60)), "NPCs already announced are not announced again");
    }

    @Test
    void shipsThatJoinTheFightLaterAreAnnouncedOnTheirOwn() {
        watcher.observe("Sparkneedle Tessella", at(0));
        watcher.observe("Strikeneedle Tessella", at(2));
        assertEquals("Room 1: Rogue drones.", watcher.poll(at(6)).orElseThrow().speech());

        watcher.observe("Anchoring Damavik", at(12));
        watcher.observe("Harrowing Vedmak", at(14));
        watcher.observe("Sparkneedle Tessella", at(15));
        assertEquals(Optional.empty(), watcher.poll(at(17)));

        AbyssSpawnCatalog.RoomReport later = watcher.poll(at(18)).orElseThrow();

        assertEquals("Also in room 1: Triglavians, with Vedmak. Watch for scrams.", later.speech());
        watcher.observe("Striking Damavik", at(30));
        assertEquals(Optional.empty(), watcher.poll(at(40)), "another Damavik brings nothing new");

        watcher.observe("Renewing Rodiva", at(45));
        assertEquals("Also in room 1: watch for remote repairs.", watcher.poll(at(51)).orElseThrow().speech());
    }

    @Test
    void aPauseInTheFightingStartsTheNextRoom() {
        watcher.observe("Scylla Tyrannos", at(0));
        watcher.poll(at(6));
        watcher.observe("Scylla Tyrannos", at(20));
        watcher.observe("Striking Kikimora", at(90));
        watcher.observe("Tangling Kikimora", at(92));

        AbyssSpawnCatalog.RoomReport report = watcher.poll(at(96)).orElseThrow();

        assertEquals(2, report.room());
        assertEquals(List.of("Striking Kikimora", "Tangling Kikimora"), report.npcs());
    }

    @Test
    void theRoomCountStartsAgainForTheNextRun() {
        watcher.observe("Lucid Deepwatcher", at(0));
        watcher.poll(at(6));
        watcher.observe("Sparklance Tessella", at(600));
        assertEquals(1, watcher.poll(at(606)).orElseThrow().room());

        watcher.reset();
        watcher.observe("Harrowing Vedmak", at(700));
        assertEquals(1, watcher.poll(at(706)).orElseThrow().room());
    }

    @Test
    void shotsAtCachesAndPilotsNeverOpenARoom() {
        watcher.observe("Triglavian Biocombinative Cache", at(0));
        watcher.observe("Barset", at(1));

        assertEquals(Optional.empty(), watcher.poll(at(30)));
    }
}
