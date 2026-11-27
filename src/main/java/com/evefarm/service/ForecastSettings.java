package com.evefarm.service;

import com.evefarm.db.dao.SettingsDao;
import com.evefarm.model.EveCharacter;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ForecastSettings {

    public static final String MAIN = "main";
    public static final String ALL = "all";
    public static final String GROUP_PREFIX = "group:";
    public static final String CHARACTER_PREFIX = "character:";
    public static final List<Integer> WINDOWS = List.of(14, 30, 60, 90);
    public static final int DEFAULT_WINDOW = 30;
    public static final String DEFAULT_VIEW = "Favorite Character";
    static final Duration LONGEST = Duration.ofDays(Forecast.MAX_DAYS);

    private static final Logger LOG = Logger.getLogger(ForecastSettings.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public enum Horizon {
        DAYS_30("30 days", 30), DAYS_90("90 days", 90), DAYS_180("180 days", 180), YEAR_END("End of year", 0),
        DATE("Date", 0);

        private final String label;
        private final int days;

        Horizon(String label, int days) {
            this.label = label;
            this.days = days;
        }

        public Instant end(Instant now, ZoneId zone, String date) {
            Instant end = switch (this) {
                case YEAR_END -> yearEnd(now, zone);
                case DATE -> parse(date).map(day -> day.plusDays(1).atStartOfDay(zone).toInstant())
                        .filter(at -> at.isAfter(now)).orElseGet(() -> yearEnd(now, zone));
                default -> now.plus(Duration.ofDays(days));
            };
            Instant latest = now.plus(LONGEST);
            return end.isAfter(latest) ? latest : end;
        }

        private static Instant yearEnd(Instant now, ZoneId zone) {
            Instant end = LocalDate.ofInstant(now, zone).withDayOfYear(1).plusYears(1).atStartOfDay(zone).toInstant();
            return Duration.between(now, end).compareTo(Duration.ofDays(1)) < 0
                    ? LocalDate.ofInstant(end, zone).plusYears(1).atStartOfDay(zone).toInstant() : end;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public record View(String name, String characters, String combination, Horizon horizon, String date,
                       int windowDays, Double goal) {

        public View {
            characters = characters == null || characters.isBlank() ? MAIN : characters;
            horizon = horizon == null ? Horizon.YEAR_END : horizon;
            windowDays = WINDOWS.contains(windowDays) ? windowDays : DEFAULT_WINDOW;
            goal = goal == null || goal <= 0 ? null : goal;
        }

        public static View standard(String name) {
            return new View(name, MAIN, null, Horizon.YEAR_END, null, DEFAULT_WINDOW, null);
        }

        public View withName(String value) {
            return new View(value, characters, combination, horizon, date, windowDays, goal);
        }

        public View withCharacters(String value) {
            return new View(name, value, combination, horizon, date, windowDays, goal);
        }

        public View withCombination(String value) {
            return new View(name, characters, value, horizon, date, windowDays, goal);
        }

        public View withHorizon(Horizon value, String day) {
            return new View(name, characters, combination, value, day, windowDays, goal);
        }

        public View withWindow(int value) {
            return new View(name, characters, combination, horizon, date, value, goal);
        }

        public View withGoal(Double value) {
            return new View(name, characters, combination, horizon, date, windowDays, value);
        }

        public Duration window() {
            return Duration.ofDays(windowDays);
        }
    }

    public record Group(String name, List<Long> characterIds) {

        public Group {
            characterIds = characterIds == null ? List.of() : List.copyOf(characterIds);
        }
    }

    public record Selection(String label, Set<Long> characterIds) {
    }

    private final SettingsDao settingsDao;

    public ForecastSettings(SettingsDao settingsDao) {
        this.settingsDao = settingsDao;
    }

    public List<View> views() {
        List<View> views = read(SettingsDao.FORECAST_VIEWS, View[].class);
        return views.isEmpty() ? List.of(View.standard(DEFAULT_VIEW)) : views;
    }

    public void saveViews(List<View> views) {
        write(SettingsDao.FORECAST_VIEWS, views);
    }

    public View currentView() {
        List<View> views = views();
        String name = settingsDao.getOrDefault(SettingsDao.FORECAST_VIEW, "");
        return views.stream().filter(view -> view.name().equals(name)).findFirst().orElse(views.getFirst());
    }

    public void select(String name) {
        settingsDao.set(SettingsDao.FORECAST_VIEW, name);
    }

    public void saveCurrent(View view) {
        List<View> views = views();
        String current = currentView().name();
        saveViews(views.stream().map(saved -> saved.name().equals(current) ? view : saved).toList());
        select(view.name());
    }

    public List<Group> groups() {
        return read(SettingsDao.CHARACTER_GROUPS, Group[].class);
    }

    public void saveGroups(List<Group> groups) {
        write(SettingsDao.CHARACTER_GROUPS, groups);
    }

    public static Selection resolve(String selector, List<EveCharacter> charactersMainFirst, List<Group> groups) {
        Set<Long> known = new LinkedHashSet<>();
        charactersMainFirst.forEach(character -> known.add(character.characterId()));
        if (ALL.equals(selector)) {
            return new Selection("All characters", known);
        }
        if (selector.startsWith(GROUP_PREFIX)) {
            String name = selector.substring(GROUP_PREFIX.length());
            Optional<Group> group = groups.stream().filter(candidate -> candidate.name().equals(name)).findFirst();
            if (group.isPresent()) {
                Set<Long> ids = new LinkedHashSet<>(group.get().characterIds());
                ids.retainAll(known);
                return new Selection(name, ids);
            }
        }
        if (selector.startsWith(CHARACTER_PREFIX)) {
            String id = selector.substring(CHARACTER_PREFIX.length());
            Optional<EveCharacter> character = charactersMainFirst.stream()
                    .filter(candidate -> String.valueOf(candidate.characterId()).equals(id)).findFirst();
            if (character.isPresent()) {
                return new Selection(character.get().characterName(), Set.of(character.get().characterId()));
            }
        }
        return charactersMainFirst.isEmpty() ? new Selection("No characters", Set.of())
                : new Selection(charactersMainFirst.getFirst().characterName(),
                Set.of(charactersMainFirst.getFirst().characterId()));
    }

    private static Optional<LocalDate> parse(String date) {
        if (date == null || date.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(date.strip()));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private <T> List<T> read(String key, Class<T[]> type) {
        String json = settingsDao.getOrDefault(key, "");
        if (json.isBlank()) {
            return List.of();
        }
        try {
            return List.of(MAPPER.readValue(json, type));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Couldn't read the saved " + key, e);
            return List.of();
        }
    }

    private void write(String key, Object value) {
        try {
            settingsDao.set(key, MAPPER.writeValueAsString(value));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Couldn't save the " + key, e);
        }
    }
}
