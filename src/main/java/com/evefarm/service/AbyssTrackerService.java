package com.evefarm.service;

import com.evefarm.auth.AuthService;
import com.evefarm.auth.OAuthConfig;
import com.evefarm.db.dao.AbyssalRunDao;
import com.evefarm.esi.EsiException;
import com.evefarm.esi.LocationApi;
import com.evefarm.model.AbyssFleet;
import com.evefarm.model.AbyssTier;
import com.evefarm.model.AbyssWeather;
import com.evefarm.model.AbyssalRun;
import com.evefarm.model.EveCharacter;
import com.evefarm.model.TypeInfo;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class AbyssTrackerService {

    private static final Logger LOG = Logger.getLogger(AbyssTrackerService.class.getName());
    private static final long POLL_SECONDS = 10;
    private static final long FIRST_ABYSSAL_SYSTEM = 32_000_000L;
    private static final long LAST_ABYSSAL_SYSTEM = 32_999_999L;
    private static final Set<Integer> CAPSULES = Set.of(670, 33328);
    static final Duration IDLE_LIMIT = Duration.ofMinutes(60);

    public enum Phase { STOPPED, WAITING, IN_ABYSS }

    public enum EventType { STARTED, ENTERED_ABYSS, LEFT_ABYSS, STOPPED, PROBLEM }

    public record Status(Phase phase, Long characterId, String characterName, AbyssTier tier, AbyssWeather weather,
                         AbyssFleet fleet, Instant enteredAt, String shipName, int runsSaved, String message) {
        static Status stopped(String message) {
            return new Status(Phase.STOPPED, null, null, null, null, null, null, null, 0, message);
        }
    }

    public record Event(EventType type, Status status, AbyssalRun run) {
    }

    public interface Listener {
        void onEvent(Event event);
    }

    interface LocationSource {
        long solarSystemId(long characterId);

        int shipTypeId(long characterId);
    }

    interface PollScheduler {
        Future<?> schedule(Runnable poll);
    }

    interface FilamentPricer {
        Double cost(AbyssTier tier, AbyssWeather weather, AbyssFleet fleet);
    }

    private static final class Session {
        final long characterId;
        final String characterName;
        AbyssTier tier;
        AbyssWeather weather;
        AbyssFleet fleet;
        Phase phase = Phase.WAITING;
        Instant enteredAt;
        Integer shipTypeId;
        String shipName;
        Instant lastActivity;
        int runsSaved;
        String message;
        Future<?> task;

        Session(long characterId, String characterName, AbyssTier tier, AbyssWeather weather, AbyssFleet fleet,
                Instant now) {
            this.characterId = characterId;
            this.characterName = characterName;
            this.tier = tier;
            this.weather = weather;
            this.fleet = fleet;
            this.lastActivity = now;
        }

        Status status() {
            return new Status(phase, characterId, characterName, tier, weather, fleet, enteredAt, shipName, runsSaved,
                    message);
        }
    }

    private final LocationSource locationSource;
    private final IntFunction<TypeInfo> shipTypes;
    private final FilamentPricer filamentCosts;
    private final AbyssalRunDao runDao;
    private final Clock clock;
    private final PollScheduler scheduler;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private Session session;
    private Status lastStatus = Status.stopped(null);

    public AbyssTrackerService(AuthService authService, LocationApi locationApi,
                               TypeNameCacheService typeNameCacheService, AbyssLootService lootService,
                               AbyssalRunDao runDao) {
        this(new LocationSource() {
                 @Override
                 public long solarSystemId(long characterId) {
                     return locationApi.getLocation(characterId, authService.getValidAccessToken(characterId))
                             .solarSystemId();
                 }

                 @Override
                 public int shipTypeId(long characterId) {
                     return locationApi.getShip(characterId, authService.getValidAccessToken(characterId))
                             .shipTypeId();
                 }
             },
                typeNameCacheService::resolveType,
                lootService::filamentCost,
                runDao,
                Clock.systemUTC(),
                defaultScheduler());
    }

    AbyssTrackerService(LocationSource locationSource, IntFunction<TypeInfo> shipTypes,
                        FilamentPricer filamentCosts, AbyssalRunDao runDao,
                        Clock clock, PollScheduler scheduler) {
        this.locationSource = locationSource;
        this.shipTypes = shipTypes;
        this.filamentCosts = filamentCosts;
        this.runDao = runDao;
        this.clock = clock;
        this.scheduler = scheduler;
    }

    private static PollScheduler defaultScheduler() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "abyss-tracker");
            thread.setDaemon(true);
            return thread;
        });
        return poll -> executor.scheduleWithFixedDelay(poll, 0, POLL_SECONDS, TimeUnit.SECONDS);
    }

    public static boolean isAbyssalSystem(long solarSystemId) {
        return solarSystemId >= FIRST_ABYSSAL_SYSTEM && solarSystemId <= LAST_ABYSSAL_SYSTEM;
    }

    public static boolean canTrack(EveCharacter character) {
        List<String> scopes = character.scopes();
        return scopes != null && scopes.contains(OAuthConfig.LOCATION_SCOPE)
                && scopes.contains(OAuthConfig.SHIP_SCOPE);
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public synchronized Status status() {
        return session == null ? lastStatus : session.status();
    }

    public void start(EveCharacter character, AbyssTier tier, AbyssWeather weather, AbyssFleet fleet) {
        if (!canTrack(character)) {
            throw new IllegalStateException(character.characterName()
                    + " has not allowed EVE Farm to read its location yet. Log this character in again from "
                    + "Characters, then start tracking.");
        }
        Status status;
        synchronized (this) {
            cancelTask();
            session = new Session(character.characterId(), character.characterName(), tier, weather, fleet,
                    clock.instant());
            session.message = "Waiting for " + character.characterName() + " to enter the Abyss";
            status = session.status();
            session.task = scheduler.schedule(this::poll);
        }
        fire(new Event(EventType.STARTED, status, null));
    }

    public synchronized void changeFilament(AbyssTier tier, AbyssWeather weather, AbyssFleet fleet) {
        if (session != null) {
            session.tier = tier;
            session.weather = weather;
            session.fleet = fleet;
        }
    }

    public void stop() {
        stop("Tracking stopped");
    }

    private void stop(String message) {
        Status status;
        synchronized (this) {
            if (session == null) {
                return;
            }
            cancelTask();
            lastStatus = new Status(Phase.STOPPED, session.characterId, session.characterName, session.tier,
                    session.weather, session.fleet, null, null, session.runsSaved, message);
            session = null;
            status = lastStatus;
        }
        fire(new Event(EventType.STOPPED, status, null));
    }

    private void cancelTask() {
        if (session != null && session.task != null) {
            session.task.cancel(false);
        }
    }

    void poll() {
        Session current;
        synchronized (this) {
            current = session;
        }
        if (current == null) {
            return;
        }
        try {
            boolean inAbyss = isAbyssalSystem(locationSource.solarSystemId(current.characterId));
            Phase phase;
            synchronized (this) {
                if (session != current) {
                    return;
                }
                phase = current.phase;
            }
            if (phase == Phase.WAITING && inAbyss) {
                enterAbyss(current);
            } else if (phase == Phase.IN_ABYSS && !inAbyss) {
                leaveAbyss(current);
            } else if (phase == Phase.WAITING
                    && Duration.between(current.lastActivity, clock.instant()).compareTo(IDLE_LIMIT) >= 0) {
                stop("Tracking stopped after " + IDLE_LIMIT.toMinutes() + " minutes without a run");
            } else {
                clearProblem(current);
            }
        } catch (EsiException e) {
            if (e.statusCode() == 401 || e.statusCode() == 403) {
                stop("EVE refused to share the location of " + current.characterName
                        + ". Log this character in again from Characters, then start tracking.");
            } else {
                problem(current, "Couldn't read the location (HTTP " + e.statusCode() + "), trying again");
            }
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Abyss tracking poll failed", e);
            problem(current, "Couldn't read the location, trying again");
        }
    }

    private void enterAbyss(Session current) {
        Instant now = clock.instant();
        Integer shipTypeId = shipTypeOrNull(current.characterId);
        TypeInfo ship = shipTypeId == null ? null : shipInfoOrNull(shipTypeId);
        String shipName = ship == null ? null : ship.name();
        AbyssFleet shipFleet = ship == null ? null : AbyssFleet.forShipGroup(ship.groupName());
        Status status;
        synchronized (this) {
            if (session != current) {
                return;
            }
            if (shipFleet != null) {
                current.fleet = shipFleet;
            }
            current.phase = Phase.IN_ABYSS;
            current.enteredAt = now;
            current.shipTypeId = shipTypeId;
            current.shipName = shipName;
            current.message = current.characterName + " entered the Abyss";
            status = current.status();
        }
        fire(new Event(EventType.ENTERED_ABYSS, status, null));
    }

    private void leaveAbyss(Session current) {
        Instant now = clock.instant();
        Integer exitShip = shipTypeOrNull(current.characterId);
        AbyssTier tier;
        AbyssWeather weather;
        AbyssFleet fleet;
        synchronized (this) {
            tier = current.tier;
            weather = current.weather;
            fleet = current.fleet;
        }
        boolean survived = survived(current.shipTypeId, exitShip);
        int seconds = (int) Math.max(1, Duration.between(current.enteredAt, now).toSeconds());
        AbyssalRun run = new AbyssalRun(0, current.characterId, current.characterName, current.enteredAt, seconds,
                tier, weather, fleet, current.shipTypeId, current.shipName, survived, 0,
                filamentCostOrNull(tier, weather, fleet), null);
        run = run.withId(runDao.save(run, List.of()));
        Status status;
        synchronized (this) {
            current.phase = Phase.WAITING;
            current.enteredAt = null;
            current.lastActivity = now;
            current.runsSaved++;
            current.message = survived
                    ? current.characterName + " left the Abyss, run saved"
                    : current.characterName + " left the Abyss in a capsule, run saved as lost";
            status = session == current ? current.status() : lastStatus;
        }
        fire(new Event(EventType.LEFT_ABYSS, status, run));
    }

    static boolean survived(Integer entryShip, Integer exitShip) {
        if (exitShip == null || !CAPSULES.contains(exitShip)) {
            return true;
        }
        return entryShip != null && CAPSULES.contains(entryShip);
    }

    private void problem(Session current, String message) {
        Status status;
        synchronized (this) {
            if (session != current) {
                return;
            }
            current.message = message;
            status = current.status();
        }
        fire(new Event(EventType.PROBLEM, status, null));
    }

    private void clearProblem(Session current) {
        synchronized (this) {
            if (session != current || current.message == null || !current.message.startsWith("Couldn't")) {
                return;
            }
            current.message = current.phase == Phase.IN_ABYSS
                    ? current.characterName + " is in the Abyss"
                    : "Waiting for " + current.characterName + " to enter the Abyss";
        }
        fire(new Event(EventType.PROBLEM, status(), null));
    }

    private Integer shipTypeOrNull(long characterId) {
        try {
            return locationSource.shipTypeId(characterId);
        } catch (RuntimeException e) {
            LOG.log(Level.FINE, "Couldn't read the current ship", e);
            return null;
        }
    }

    private TypeInfo shipInfoOrNull(int typeId) {
        try {
            return shipTypes.apply(typeId);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Double filamentCostOrNull(AbyssTier tier, AbyssWeather weather, AbyssFleet fleet) {
        try {
            return filamentCosts.cost(tier, weather, fleet);
        } catch (RuntimeException e) {
            LOG.log(Level.FINE, "Couldn't price the filament", e);
            return null;
        }
    }

    private void fire(Event event) {
        for (Listener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Abyss tracker listener failed", e);
            }
        }
    }
}
