package com.evefarm.service;

import com.evefarm.auth.OAuthConfig;
import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.db.dao.AbyssalRunDao;
import com.evefarm.db.dao.CharacterDao;
import com.evefarm.esi.EsiException;
import com.evefarm.model.AbyssFleet;
import com.evefarm.model.AbyssTier;
import com.evefarm.model.AbyssWeather;
import com.evefarm.model.AbyssalRun;
import com.evefarm.model.EveCharacter;
import com.evefarm.model.TypeInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbyssTrackerServiceTest {

    private static final long PILOT = 90_000_001L;
    private static final long JITA = 30_000_142L;
    private static final long ABYSSAL_POCKET = 32_000_057L;
    private static final int GILA = 17715;
    private static final int CAPSULE = 670;
    private static final int RETRIBUTION = 11393;
    private static final Instant START = Instant.parse("2026-09-28T18:00:00Z");
    private static final EveCharacter CHARACTER = new EveCharacter(PILOT, "Abyss Runner", null,
            List.of(OAuthConfig.LOCATION_SCOPE, OAuthConfig.SHIP_SCOPE), START, true);

    private final Deque<Object> locations = new ArrayDeque<>();
    private final Deque<Integer> ships = new ArrayDeque<>();
    private final List<AbyssTrackerService.Event> events = new ArrayList<>();
    private final MutableClock clock = new MutableClock(START);
    private AbyssalRunDao runs;
    private AbyssTrackerService tracker;

    @BeforeEach
    void setUp() {
        Database database = new Database(":memory:");
        MigrationRunner.run(database);
        new CharacterDao(database).upsert(PILOT, "Abyss Runner", null, CHARACTER.scopes(), "owner");
        runs = new AbyssalRunDao(database);
        AbyssTrackerService.LocationSource source = new AbyssTrackerService.LocationSource() {
            @Override
            public long solarSystemId(long characterId) {
                Object next = locations.removeFirst();
                if (next instanceof RuntimeException e) {
                    throw e;
                }
                return (Long) next;
            }

            @Override
            public int shipTypeId(long characterId) {
                return ships.removeFirst();
            }
        };
        tracker = new AbyssTrackerService(source, AbyssTrackerServiceTest::shipType,
                (tier, weather, fleet) -> tier == AbyssTier.FIERCE ? 9_500_000.0 * AbyssFleet.shipsOf(fleet) : null,
                runs, clock,
                poll -> new CompletableFuture<>());
        tracker.addListener(events::add);
    }

    @Test
    void aRunIsTimedFromEnteringTheAbyssToLeavingItAndSaved() {
        tracker.start(CHARACTER, AbyssTier.FIERCE, AbyssWeather.EXOTIC, AbyssFleet.CRUISER);
        pollAt(0, JITA);
        ships.add(GILA);
        pollAt(10, ABYSSAL_POCKET);
        pollAt(300, ABYSSAL_POCKET);
        ships.add(GILA);
        pollAt(764, JITA);

        assertEquals(List.of(AbyssTrackerService.EventType.STARTED, AbyssTrackerService.EventType.ENTERED_ABYSS,
                AbyssTrackerService.EventType.LEFT_ABYSS), types());
        AbyssalRun run = events.get(2).run();
        assertNotNull(run);
        assertEquals(754, run.durationSeconds());
        assertEquals(START.plusSeconds(10), run.startedAt());
        assertEquals("Gila", run.shipName());
        assertEquals(GILA, run.shipTypeId());
        assertTrue(run.survived());
        assertEquals(9_500_000.0, run.filamentCost());
        assertEquals(List.of(run), runs.listRuns());
        assertEquals(AbyssTrackerService.Phase.WAITING, tracker.status().phase());
        assertEquals(1, tracker.status().runsSaved());
    }

    @Test
    void leavingTheAbyssInACapsuleSavesTheRunAsLost() {
        tracker.start(CHARACTER, AbyssTier.RAGING, AbyssWeather.DARK, AbyssFleet.CRUISER);
        ships.add(GILA);
        pollAt(0, ABYSSAL_POCKET);
        ships.add(CAPSULE);
        pollAt(420, JITA);

        AbyssalRun run = events.get(2).run();
        assertFalse(run.survived());
        assertNull(run.filamentCost());
        assertEquals(AbyssTier.RAGING, run.tier());
    }

    @Test
    void theFilamentCanBeChangedWhileTracking() {
        tracker.start(CHARACTER, AbyssTier.CALM, AbyssWeather.DARK, AbyssFleet.CRUISER);
        ships.add(GILA);
        pollAt(0, ABYSSAL_POCKET);
        tracker.changeFilament(AbyssTier.FIERCE, AbyssWeather.GAMMA, AbyssFleet.FRIGATES);
        ships.add(GILA);
        pollAt(600, JITA);

        AbyssalRun run = events.get(2).run();
        assertEquals(AbyssTier.FIERCE, run.tier());
        assertEquals(AbyssWeather.GAMMA, run.weather());
        assertEquals(AbyssFleet.FRIGATES, run.fleet());
        assertEquals(28_500_000.0, run.filamentCost(), "three frigates use three filaments");
    }

    @Test
    void trackingStopsAfterAnHourWithoutARun() {
        tracker.start(CHARACTER, AbyssTier.FIERCE, AbyssWeather.EXOTIC, AbyssFleet.CRUISER);
        pollAt(0, JITA);
        pollAt(AbyssTrackerService.IDLE_LIMIT.toSeconds() - 1, JITA);
        assertEquals(AbyssTrackerService.Phase.WAITING, tracker.status().phase());

        pollAt(AbyssTrackerService.IDLE_LIMIT.toSeconds(), JITA);

        assertEquals(AbyssTrackerService.EventType.STOPPED, events.getLast().type());
        assertEquals(AbyssTrackerService.Phase.STOPPED, tracker.status().phase());
        pollAt(AbyssTrackerService.IDLE_LIMIT.toSeconds() + 10, JITA);
        assertEquals(1, locations.size(), "a stopped tracker must not ask EVE for the location");
    }

    @Test
    void aRefusedLocationStopsTrackingAndAsksForANewLogin() {
        tracker.start(CHARACTER, AbyssTier.FIERCE, AbyssWeather.EXOTIC, AbyssFleet.CRUISER);
        locations.add(new EsiException(403, "token is not valid for scope(s): esi-location.read_location.v1"));
        tracker.poll();

        assertEquals(AbyssTrackerService.Phase.STOPPED, tracker.status().phase());
        assertTrue(tracker.status().message().contains("Log this character in again"));
    }

    @Test
    void aTemporaryErrorKeepsTracking() {
        tracker.start(CHARACTER, AbyssTier.FIERCE, AbyssWeather.EXOTIC, AbyssFleet.CRUISER);
        locations.add(new EsiException(502, "bad gateway"));
        tracker.poll();

        assertEquals(AbyssTrackerService.EventType.PROBLEM, events.getLast().type());
        assertEquals(AbyssTrackerService.Phase.WAITING, tracker.status().phase());

        pollAt(10, JITA);
        assertTrue(tracker.status().message().startsWith("Waiting for"));
    }

    @Test
    void aCharacterWithoutTheLocationScopesCannotBeTracked() {
        EveCharacter oldLogin = new EveCharacter(PILOT, "Abyss Runner", null, List.of("esi-assets.read_assets.v1"),
                START, true);

        assertFalse(AbyssTrackerService.canTrack(oldLogin));
        assertThrows(IllegalStateException.class,
                () -> tracker.start(oldLogin, AbyssTier.FIERCE, AbyssWeather.EXOTIC, AbyssFleet.CRUISER));
    }

    @Test
    void onlyAbyssalSystemsCountAsTheAbyss() {
        assertTrue(AbyssTrackerService.isAbyssalSystem(32_000_001L));
        assertTrue(AbyssTrackerService.isAbyssalSystem(32_000_200L));
        assertFalse(AbyssTrackerService.isAbyssalSystem(JITA));
        assertFalse(AbyssTrackerService.isAbyssalSystem(31_000_005L));
    }

    @Test
    void aCapsuleAfterTheRunMeansTheShipWasLost() {
        assertTrue(AbyssTrackerService.survived(GILA, GILA));
        assertFalse(AbyssTrackerService.survived(GILA, CAPSULE));
        assertFalse(AbyssTrackerService.survived(null, CAPSULE));
        assertTrue(AbyssTrackerService.survived(GILA, null));
    }

    @Test
    void theFleetFollowsTheHullOfTheShipThatEnters() {
        tracker.start(CHARACTER, AbyssTier.FIERCE, AbyssWeather.EXOTIC, AbyssFleet.CRUISER);
        ships.add(RETRIBUTION);
        pollAt(0, ABYSSAL_POCKET);
        assertEquals(AbyssFleet.FRIGATES, tracker.status().fleet());
        ships.add(RETRIBUTION);
        pollAt(500, JITA);

        AbyssalRun run = events.get(2).run();
        assertEquals("Retribution", run.shipName());
        assertEquals(AbyssFleet.FRIGATES, run.fleet());
        assertEquals(28_500_000.0, run.filamentCost(), "a frigate fleet always uses three filaments");
    }

    @Test
    void shipGroupsAreSortedIntoFleetSizes() {
        assertEquals(AbyssFleet.FRIGATES, AbyssFleet.forShipGroup("Assault Frigate"));
        assertEquals(AbyssFleet.FRIGATES, AbyssFleet.forShipGroup("Interceptor"));
        assertEquals(AbyssFleet.DESTROYERS, AbyssFleet.forShipGroup("Tactical Destroyer"));
        assertEquals(AbyssFleet.DESTROYERS, AbyssFleet.forShipGroup("Interdictor"));
        assertEquals(AbyssFleet.CRUISER, AbyssFleet.forShipGroup("Heavy Assault Cruiser"));
        assertEquals(AbyssFleet.CRUISER, AbyssFleet.forShipGroup("Logistics"));
        assertNull(AbyssFleet.forShipGroup("Capsule"));
        assertNull(AbyssFleet.forShipGroup(null));
    }

    private static TypeInfo shipType(int typeId) {
        return switch (typeId) {
            case GILA -> new TypeInfo(GILA, "Gila", "Cruiser", "Ship", 0);
            case RETRIBUTION -> new TypeInfo(RETRIBUTION, "Retribution", "Assault Frigate", "Ship", 0);
            default -> new TypeInfo(typeId, "Capsule", "Capsule", "Ship", 0);
        };
    }

    private void pollAt(long secondsAfterStart, long solarSystemId) {
        clock.set(START.plusSeconds(secondsAfterStart));
        locations.add(solarSystemId);
        tracker.poll();
    }

    private List<AbyssTrackerService.EventType> types() {
        return events.stream().map(AbyssTrackerService.Event::type).toList();
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void set(Instant instant) {
            now = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
