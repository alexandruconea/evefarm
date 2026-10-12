package com.evefarm.service;

import com.evefarm.auth.AuthService;
import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.db.dao.CharacterDao;
import com.evefarm.db.dao.EntityNameCacheDao;
import com.evefarm.db.dao.LoyaltyPointDao;
import com.evefarm.esi.LoyaltyApi;
import com.evefarm.esi.dto.LoyaltyPointDto;
import com.evefarm.model.LoyaltyPointCheck;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoyaltyPointServiceTest {

    private static final long PILOT = 2124165849L;
    private static final long CORPORATE_POLICE_FORCE = 1000043L;
    private static final Instant NOW = Instant.parse("2026-10-12T15:20:00Z");

    private final AuthService auth = mock(AuthService.class);
    private final LoyaltyApi loyalty = mock(LoyaltyApi.class);
    private LoyaltyPointDao points;

    @BeforeEach
    void setUp() {
        Database database = new Database(":memory:");
        MigrationRunner.run(database);
        new CharacterDao(database).upsert(PILOT, "Malpais Legate", List.of(), "owner");
        points = new LoyaltyPointDao(database);
        when(auth.getValidAccessToken(PILOT)).thenReturn("token");
    }

    private LoyaltyPointService serviceAt(Instant now) {
        return new LoyaltyPointService(auth, loyalty, mock(EntityNameCacheService.class),
                mock(EntityNameCacheDao.class), points, Clock.fixed(now, ZoneOffset.UTC));
    }

    private void eveAnswers(long loyaltyPoints, Duration age, Duration freshFor) {
        when(loyalty.readLoyaltyPoints(PILOT, "token")).thenReturn(new LoyaltyApi.LoyaltyPoints(
                List.of(new LoyaltyPointDto(CORPORATE_POLICE_FORCE, loyaltyPoints)), age, freshFor));
    }

    @Test
    void lpIsSavedAtTheTimeEvesDataIsFrom() {
        eveAnswers(468_489, Duration.ofMinutes(15), Duration.ofMinutes(45));

        assertTrue(serviceAt(NOW).refreshLoyaltyPointsForCharacter(PILOT));

        assertEquals(new LoyaltyPointCheck(NOW.minus(Duration.ofMinutes(15)),
                NOW.plus(Duration.ofMinutes(45)).plusSeconds(30)), points.checks().get(PILOT),
                "EVE made this data 15 minutes ago and has new data in 45");
        assertEquals(468_489, points.listForCharacter(PILOT).getFirst().loyaltyPoints());
    }

    @Test
    void eveIsAskedAgainOnlyOnceItHasNewData() {
        eveAnswers(468_489, Duration.ZERO, Duration.ofHours(1));
        serviceAt(NOW).refreshLoyaltyPointsForCharacter(PILOT);
        eveAnswers(559_258, Duration.ZERO, Duration.ofHours(1));

        assertFalse(serviceAt(NOW.plus(Duration.ofMinutes(59))).refreshLoyaltyPointsForCharacter(PILOT));
        verify(loyalty, times(1)).readLoyaltyPoints(PILOT, "token");
        assertEquals(468_489, points.listForCharacter(PILOT).getFirst().loyaltyPoints());

        assertTrue(serviceAt(NOW.plus(Duration.ofMinutes(61))).refreshLoyaltyPointsForCharacter(PILOT));
        verify(loyalty, times(2)).readLoyaltyPoints(PILOT, "token");
        assertEquals(559_258, points.listForCharacter(PILOT).getFirst().loyaltyPoints());
        assertEquals(List.of(90_769L), points.listGains().stream().map(gain -> gain.points()).toList());
    }

    @Test
    void withoutCacheTimesTheNextReadIsAnHourLater() {
        assertEquals(NOW.plus(Duration.ofHours(1)).plusSeconds(30), LoyaltyPointService.nextRead(NOW,
                new LoyaltyApi.LoyaltyPoints(List.of(), Duration.ZERO, null)));
        assertEquals(NOW.plus(Duration.ofHours(1)).plusSeconds(30), LoyaltyPointService.nextRead(NOW,
                        new LoyaltyApi.LoyaltyPoints(List.of(), Duration.ZERO, Duration.ofHours(6))),
                "never more than EVE's hour");
        assertEquals(NOW.plusSeconds(30), LoyaltyPointService.nextRead(NOW,
                new LoyaltyApi.LoyaltyPoints(List.of(), Duration.ZERO, Duration.ZERO)), "EVE has new data right away");
    }
}
