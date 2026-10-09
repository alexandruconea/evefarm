package com.evefarm.service;

import com.evefarm.auth.AuthService;
import com.evefarm.auth.OAuthConfig;
import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.db.dao.NotificationDao;
import com.evefarm.db.dao.OrderCompetitionDao;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.esi.ContractsApi;
import com.evefarm.esi.MarketsApi;
import com.evefarm.esi.SkillsApi;
import com.evefarm.esi.dto.ContractDto;
import com.evefarm.esi.dto.MarketOrderDto;
import com.evefarm.esi.dto.SkillQueueDto;
import com.evefarm.model.EveCharacter;
import com.evefarm.model.IndustryJobRow;
import com.evefarm.model.TypeInfo;
import com.evefarm.service.NotificationService.Notification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotificationServiceTest {

    private static final long PILOT = 42L;
    private static final long JITA = 60003760L;
    private static final long FORGE = 10000002L;
    private static final int TRITANIUM = 34;
    private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");

    private final AuthService auth = mock(AuthService.class);
    private final CharacterService characters = mock(CharacterService.class);
    private final IndustryJobService jobs = mock(IndustryJobService.class);
    private final MarketsApi markets = mock(MarketsApi.class);
    private final SkillsApi skills = mock(SkillsApi.class);
    private final ContractsApi contracts = mock(ContractsApi.class);
    private final TypeNameCacheService types = mock(TypeNameCacheService.class);
    private final List<Notification> sent = new ArrayList<>();
    private SettingsDao settings;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        Database database = new Database(":memory:");
        MigrationRunner.run(database);
        settings = new SettingsDao(database);
        for (String key : List.of(SettingsDao.NOTIFY_INDUSTRY_JOBS, SettingsDao.NOTIFY_OUTBID_ORDERS,
                SettingsDao.NOTIFY_SKILL_QUEUE, SettingsDao.NOTIFY_CONTRACTS)) {
            settings.set(key, "false");
        }
        when(auth.getValidAccessToken(PILOT)).thenReturn("token");
        when(characters.listCharacters()).thenReturn(List.of(new EveCharacter(PILOT, "Pilot",
                List.of(OAuthConfig.SKILL_QUEUE_SCOPE), NOW)));
        when(types.resolveType(TRITANIUM)).thenReturn(new TypeInfo(TRITANIUM, "Tritanium", null, null, 0.01));
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        MarketWatchService watch = new MarketWatchService(auth, markets, new OrderCompetitionDao(database), clock);
        service = new NotificationService(auth, characters, jobs, watch, skills, contracts, types,
                new NotificationDao(database), settings, clock);
        service.addListener(sent::add);
    }

    private static IndustryJobRow job(long jobId, String status, Instant end) {
        return new IndustryJobRow(PILOT, "Pilot", jobId, 1, status, "Rifter Blueprint", "Rifter", 10, 1_000.0,
                "Jita", NOW.minus(Duration.ofDays(1)).toString(), end.toString());
    }

    private static MarketOrderDto order(long orderId, boolean buy, double price, long location) {
        return new MarketOrderDto(orderId, TRITANIUM, 100, 100, price, buy, null, null, location, null, 90, "station",
                1L, FORGE);
    }

    private static ContractDto contract(long contractId, String type, String status, int issuer, Instant expires,
                                        Instant accepted) {
        return new ContractDto(contractId, type, status, "", 0.0, 1_000.0, 0.0, 1.0, NOW.toString(),
                expires.toString(), null, false, issuer, null, null, JITA, JITA,
                accepted == null ? null : accepted.toString());
    }

    @Test
    void aReadyJobIsNotifiedOnceAndANewOneAgain() {
        settings.set(SettingsDao.NOTIFY_INDUSTRY_JOBS, "true");
        when(jobs.getJobRows(Set.of(PILOT))).thenReturn(List.of(
                job(1, "active", NOW.minusSeconds(60)),
                job(2, "active", NOW.plusSeconds(3600)),
                job(3, "delivered", NOW.minusSeconds(7200))));

        service.check();
        service.check();

        assertEquals(List.of(new Notification("Industry job ready", "Pilot: Rifter, 10 runs")), sent);

        when(jobs.getJobRows(Set.of(PILOT))).thenReturn(List.of(
                job(1, "delivered", NOW.minusSeconds(60)),
                job(2, "active", NOW.minusSeconds(1))));
        service.check();

        assertEquals(2, sent.size());
        assertEquals("Industry job ready", sent.get(1).caption());
    }

    @Test
    void anOrderBeatenAtItsOwnStationIsNotifiedUntilItLeadsAgain() {
        settings.set(SettingsDao.NOTIFY_OUTBID_ORDERS, "true");
        MarketOrderDto mine = order(100, false, 10.0, JITA);
        when(markets.listCharacterOrders(PILOT, "token")).thenReturn(List.of(mine));
        when(markets.listRegionOrders(FORGE, TRITANIUM)).thenReturn(List.of(mine, order(200, false, 9.5, JITA),
                order(300, false, 5.0, 60008494L)));

        service.check();
        service.check();

        assertEquals(List.of(new Notification("Market order outbid",
                "Pilot: Tritanium sell order, 9.50 ISK vs your 10.00 ISK")), sent);

        when(markets.listRegionOrders(FORGE, TRITANIUM)).thenReturn(List.of(mine));
        service.check();
        when(markets.listRegionOrders(FORGE, TRITANIUM)).thenReturn(List.of(mine, order(200, false, 9.9, JITA)));
        service.check();

        assertEquals(2, sent.size());
    }

    @Test
    void aSkillQueueEndingWithinADayOrEmptyIsNotified() {
        settings.set(SettingsDao.NOTIFY_SKILL_QUEUE, "true");
        when(skills.getSkillQueue(PILOT, "token")).thenReturn(List.of(
                new SkillQueueDto(3300, 4, 0, NOW.plus(Duration.ofHours(2)).toString()),
                new SkillQueueDto(3300, 5, 1, NOW.plus(Duration.ofMinutes(320)).toString())));

        service.check();

        assertEquals(List.of(new Notification("Skill queue ending soon", "Pilot: the skill queue ends in 5 h 20 min")),
                sent);

        when(skills.getSkillQueue(PILOT, "token")).thenReturn(List.of());
        service.check();

        assertEquals(new Notification("Skill queue ending soon", "Pilot: the skill queue is empty"), sent.get(1));
    }

    @Test
    void theEndOfAQueueIsItsLastFinishUnlessItIsPaused() {
        assertEquals(Optional.of(Instant.EPOCH), NotificationService.queueEnd(List.of()));
        assertEquals(Optional.empty(), NotificationService.queueEnd(List.of(new SkillQueueDto(1, 1, 0, null))));
        assertEquals(Optional.of(NOW.plusSeconds(90)), NotificationService.queueEnd(List.of(
                new SkillQueueDto(1, 1, 0, NOW.plusSeconds(30).toString()),
                new SkillQueueDto(1, 2, 1, NOW.plusSeconds(90).toString()))));
    }

    @Test
    void onlyYourOwnContractsAreNotifiedWhenAcceptedOrAboutToExpire() {
        settings.set(SettingsDao.NOTIFY_CONTRACTS, "true");
        when(contracts.listContracts(PILOT, "token")).thenReturn(List.of(
                contract(1, "courier", "in_progress", 42, NOW.plus(Duration.ofDays(3)), NOW.minusSeconds(600)),
                contract(2, "item_exchange", "finished", 42, NOW.plus(Duration.ofDays(3)),
                        NOW.minus(Duration.ofDays(3))),
                contract(3, "item_exchange", "outstanding", 42, NOW.plus(Duration.ofHours(3)), null),
                contract(4, "item_exchange", "outstanding", 7, NOW.plus(Duration.ofHours(3)), null),
                contract(5, "item_exchange", "outstanding", 42, NOW.plus(Duration.ofDays(5)), null)));

        service.check();
        service.check();

        assertEquals(List.of(
                new Notification("Contract accepted", "Pilot: courier contract was accepted"),
                new Notification("Contract expiring", "Pilot: item exchange contract expires in 3 h")), sent);
    }

    @Test
    void anAcceptanceCountsOnlyForADay() {
        ContractDto recent = contract(1, "item_exchange", "finished", 42, NOW, NOW.minusSeconds(60));
        ContractDto old = contract(2, "item_exchange", "finished", 42, NOW, NOW.minus(Duration.ofHours(25)));
        ContractDto open = contract(3, "item_exchange", "outstanding", 42, NOW, null);

        assertTrue(NotificationService.acceptedRecently(recent, NOW));
        assertFalse(NotificationService.acceptedRecently(old, NOW));
        assertFalse(NotificationService.acceptedRecently(open, NOW));
    }

    @Test
    void longListsAndTimesAreShortened() {
        assertEquals("a\nb\nc\nd\nand 2 more", NotificationService.text(List.of("a", "b", "c", "d", "e", "f")));
        assertEquals("45 min", NotificationService.timeLeft(Duration.ofMinutes(45)));
        assertEquals("1 min", NotificationService.timeLeft(Duration.ofSeconds(10)));
        assertEquals("3 h", NotificationService.timeLeft(Duration.ofHours(3)));
        assertEquals("23 h 59 min", NotificationService.timeLeft(Duration.ofMinutes(23 * 60 + 59)));
    }

    @Test
    void noNotificationIsShownWhenTheyAreOff() {
        service.check();

        assertTrue(sent.isEmpty());
    }
}
