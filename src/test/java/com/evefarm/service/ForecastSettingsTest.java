package com.evefarm.service;

import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.model.EveCharacter;
import com.evefarm.service.ForecastSettings.Group;
import com.evefarm.service.ForecastSettings.Horizon;
import com.evefarm.service.ForecastSettings.View;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ForecastSettingsTest {

    private static final ZoneId BUCHAREST = ZoneId.of("Europe/Bucharest");
    private static final Instant NOW = Instant.parse("2026-11-27T10:00:00Z");
    private static final List<EveCharacter> CHARACTERS = List.of(
            new EveCharacter(1, "Main", List.of(), NOW), new EveCharacter(2, "Alt One", List.of(), NOW),
            new EveCharacter(3, "Alt Two", List.of(), NOW));

    private ForecastSettings settings;

    @BeforeEach
    void setUp() {
        Database database = new Database(":memory:");
        MigrationRunner.run(database);
        settings = new ForecastSettings(new SettingsDao(database));
    }

    @Test
    void thereIsAlwaysAViewAndChangesStayInTheOneYouAreOn() {
        View first = settings.currentView();
        assertEquals(ForecastSettings.DEFAULT_VIEW, first.name());
        assertEquals(ForecastSettings.MAIN, first.characters());

        settings.saveViews(List.of(first, View.standard("Alts").withCharacters("group:Alts")));
        settings.select("Alts");
        settings.saveCurrent(settings.currentView().withGoal(100e9).withWindow(60));

        View alts = settings.currentView();
        assertEquals(100e9, alts.goal());
        assertEquals(60, alts.windowDays());
        assertEquals(first, settings.views().getFirst(), "the other view is untouched");
    }

    @Test
    void oddValuesFallBackToSafeOnes() {
        View view = new View("Odd", null, null, null, null, 7, -5.0);

        assertEquals(ForecastSettings.MAIN, view.characters());
        assertEquals(Horizon.YEAR_END, view.horizon());
        assertEquals(ForecastSettings.DEFAULT_WINDOW, view.windowDays());
        assertNull(view.goal());
    }

    @Test
    void groupsAndCharactersAreFoundAndMissingOnesFallBackToTheFavorite() {
        List<Group> groups = List.of(new Group("Alts", List.of(2L, 3L, 99L)));

        assertEquals(Set.of(1L, 2L, 3L), ForecastSettings.resolve("all", CHARACTERS, groups).characterIds());
        assertEquals(Set.of(2L, 3L), ForecastSettings.resolve("group:Alts", CHARACTERS, groups).characterIds(),
                "a removed character is left out");
        assertEquals("Alt Two", ForecastSettings.resolve("character:3", CHARACTERS, groups).label());
        assertEquals(Set.of(1L), ForecastSettings.resolve("group:Gone", CHARACTERS, groups).characterIds());
        assertEquals(Set.of(1L), ForecastSettings.resolve("main", CHARACTERS, groups).characterIds());
        assertEquals(Set.of(), ForecastSettings.resolve("main", List.of(), groups).characterIds());
    }

    @Test
    void horizonsEndWhereTheySay() {
        assertEquals(NOW.plus(Duration.ofDays(90)), Horizon.DAYS_90.end(NOW, BUCHAREST, null));
        assertEquals(Instant.parse("2026-12-31T22:00:00Z"), Horizon.YEAR_END.end(NOW, BUCHAREST, null));
        assertEquals(Instant.parse("2027-12-31T22:00:00Z"),
                Horizon.YEAR_END.end(Instant.parse("2026-12-31T12:00:00Z"), BUCHAREST, null),
                "on the last day of the year the next one is shown");
        assertEquals(Instant.parse("2027-03-31T21:00:00Z"), Horizon.DATE.end(NOW, BUCHAREST, "2027-03-31"));
        assertEquals(Horizon.YEAR_END.end(NOW, BUCHAREST, null), Horizon.DATE.end(NOW, BUCHAREST, "2020-01-01"),
                "a day in the past falls back to the end of the year");
        assertEquals(NOW.plus(Duration.ofDays(Forecast.MAX_DAYS)), Horizon.DATE.end(NOW, BUCHAREST, "2040-01-01"));
    }

    @Test
    void groupsAreKept() {
        settings.saveGroups(List.of(new Group("Miners", List.of(2L))));

        assertEquals(List.of(new Group("Miners", List.of(2L))), settings.groups());
    }
}
