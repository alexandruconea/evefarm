package com.evefarm.service;

import com.evefarm.auth.AuthService;
import com.evefarm.auth.OAuthConfig;
import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.db.dao.CharacterDao;
import com.evefarm.db.dao.CorporationDao;
import com.evefarm.db.dao.CorporationIndustryDao;
import com.evefarm.db.dao.CorporationMarketDao;
import com.evefarm.esi.CorporationApi;
import com.evefarm.esi.EsiException;
import com.evefarm.esi.dto.AffiliationDto;
import com.evefarm.esi.dto.BlueprintDto;
import com.evefarm.esi.dto.ContractItemDto;
import com.evefarm.esi.dto.CorporationDivisionsDto;
import com.evefarm.esi.dto.CorporationInfoDto;
import com.evefarm.esi.dto.CorporationWalletDto;
import com.evefarm.esi.dto.IndustryJobDto;
import com.evefarm.esi.dto.MarketOrderDto;
import com.evefarm.model.BlueprintRow;
import com.evefarm.model.ContractEntry;
import com.evefarm.model.CorporationAccess;
import com.evefarm.model.EveCharacter;
import com.evefarm.model.IndustryJobRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CorporationServiceTest {

    private static final long CORPORATION = 98_000_001L;
    private static final Instant NOW = Instant.parse("2026-11-28T10:00:00Z");
    private static final List<String> ALL_SCOPES = Stream.concat(OAuthConfig.SCOPES.stream(),
            OAuthConfig.CORPORATION_SCOPES.stream()).toList();

    private final AuthService auth = mock(AuthService.class);
    private final CorporationApi api = mock(CorporationApi.class);
    private final TransactionService transactions = mock(TransactionService.class);
    private CorporationDao dao;
    private CorporationMarketDao market;
    private CorporationIndustryDao industry;
    private CorporationService service;

    @BeforeEach
    void setUp() {
        Database database = new Database(":memory:");
        MigrationRunner.run(database);
        CharacterDao characters = new CharacterDao(database);
        characters.upsert(1, "Accountant Alt", ALL_SCOPES, "a");
        characters.upsert(2, "Ceo Main", ALL_SCOPES, "b");
        characters.upsert(3, "Plain Alt", OAuthConfig.SCOPES, "c");
        dao = new CorporationDao(database);
        market = new CorporationMarketDao(database, Clock.fixed(NOW, ZoneOffset.UTC));
        industry = new CorporationIndustryDao(database);
        service = new CorporationService(auth, api, dao, market, industry, mock(AssetService.class), transactions,
                mock(EntityNameCacheService.class), mock(TypeNameCacheService.class),
                mock(LocationNameCacheService.class), Clock.fixed(NOW, ZoneOffset.UTC));
        when(auth.getValidAccessToken(anyLong())).thenReturn("token");
        when(api.affiliations(any())).thenReturn(List.of(new AffiliationDto(1, CORPORATION, null),
                new AffiliationDto(2, CORPORATION, null)));
        when(api.corporation(CORPORATION)).thenReturn(new CorporationInfoDto("Deep Core Mining", "DCM", 2L));
        when(api.roles(1, "token")).thenReturn(List.of("Accountant"));
        when(api.roles(2, "token")).thenReturn(List.of());
        when(api.wallets(CORPORATION, "token")).thenReturn(List.of(new CorporationWalletDto(1, 7_000_000)));
        when(api.journal(anyLong(), anyInt(), anyString())).thenReturn(List.of());
        when(api.transactions(anyLong(), anyInt(), anyString())).thenReturn(List.of());
        when(transactions.prepare(any(), any())).thenReturn(List.of());
    }

    private static EveCharacter character(long id, String name, List<String> scopes) {
        return new EveCharacter(id, name, scopes, NOW);
    }

    private List<EveCharacter> characters() {
        return List.of(character(1, "Accountant Alt", ALL_SCOPES), character(2, "Ceo Main", ALL_SCOPES),
                character(3, "Plain Alt", OAuthConfig.SCOPES));
    }

    @Test
    void onlyCharactersAskedForTheCorporationAreReadAndTheCeoCountsAsDirector() {
        when(api.assets(CORPORATION, "token")).thenReturn(List.of());
        when(api.divisions(CORPORATION, "token")).thenReturn(
                new com.evefarm.esi.dto.CorporationDivisionsDto(List.of(), List.of()));
        List<String> failures = new ArrayList<>();

        service.refresh(characters(), failures);

        assertEquals(List.of(), failures);
        assertFalse(service.accessByCharacter().containsKey(3L), "not asked at login");
        assertEquals(List.of("Accountant"), service.accessByCharacter().get(1L).roles());
        assertTrue(service.accessByCharacter().get(2L).readsAssets(), "the CEO");
        assertEquals(7_000_000, dao.walletsValue(CORPORATION), 1e-9);
        assertEquals("Deep Core Mining [DCM]", service.corporations().get(CORPORATION).label());
    }

    @Test
    void aRoleTakenAwayIsNoFailure() {
        when(api.divisions(CORPORATION, "token")).thenThrow(new EsiException(403, "Character does not have role"));
        List<String> failures = new ArrayList<>();

        service.refresh(characters(), failures);

        assertEquals(List.of(), failures);
        verify(api, never()).assets(anyLong(), anyString());
    }

    @Test
    void theReaderNeedsTheRoleAndTheScope() {
        CorporationService.Reader accountant = new CorporationService.Reader(character(1, "A", ALL_SCOPES),
                new CorporationAccess(1, CORPORATION, List.of("Accountant"), NOW), "t");
        CorporationService.Reader directorWithoutScope = new CorporationService.Reader(
                character(2, "B", OAuthConfig.SCOPES), new CorporationAccess(2, CORPORATION, List.of("Director"), NOW),
                "t");
        List<CorporationService.Reader> readers = List.of(directorWithoutScope, accountant);

        assertEquals(accountant, CorporationService.readerFor(readers, CorporationAccess::readsWallets,
                OAuthConfig.CORPORATION_WALLETS_SCOPE).orElseThrow());
        assertTrue(CorporationService.readerFor(readers, CorporationAccess::readsAssets,
                OAuthConfig.CORPORATION_ASSETS_SCOPE).isEmpty());
    }

    @Test
    void ordersAndContractsNeedNoDirectorButJobsAndBlueprintsDo() {
        when(api.roles(2, "token")).thenReturn(List.of("Trader"));
        when(api.corporation(CORPORATION)).thenReturn(new CorporationInfoDto("Deep Core Mining", "DCM", 99L));
        List<String> failures = new ArrayList<>();

        service.refresh(characters(), failures);

        assertEquals(List.of(), failures);
        verify(api).orders(CORPORATION, "token");
        verify(api).contracts(CORPORATION, "token");
        verify(api, never()).industryJobs(anyLong(), anyString());
        verify(api, never()).blueprints(anyLong(), anyString());
    }

    @Test
    void aDirectorReadsJobsAndBlueprintsAndSellOrdersCountOnTheCard() {
        when(api.divisions(CORPORATION, "token")).thenReturn(new CorporationDivisionsDto(List.of(), List.of()));
        when(api.orders(CORPORATION, "token")).thenReturn(List.of(new MarketOrderDto(5, 34, 3, 3, 100.0, false, null,
                null, 60003760L, "2026-11-27T10:00:00Z", 90, "station", null, null, null, true, 1)));
        when(api.industryJobs(CORPORATION, "token")).thenReturn(List.of(new IndustryJobDto(8, 1, 100.0, "active",
                1_000, 2_000, 1, null, null, "2026-11-27T10:00:00Z", "2026-11-29T10:00:00Z", 2L)));
        when(api.blueprints(CORPORATION, "token")).thenReturn(List.of(
                new BlueprintDto(11, 1_000, 60003760L, "CorpSAG1", -1, 10, 20, -1)));
        List<String> failures = new ArrayList<>();

        service.refresh(characters(), failures);

        assertEquals(List.of(), failures);
        assertEquals(List.of(8L), service.jobRows().stream().map(IndustryJobRow::jobId).toList());
        assertEquals(List.of(11L), service.blueprintRows().stream().map(BlueprintRow::itemId).toList());
        CorporationService.Card card = service.cards().getFirst();
        assertEquals(300, card.summary().sellOrdersValue(), 1e-9);
        assertEquals(7_000_300, card.summary().total(), 1e-9);
    }

    @Test
    void aPersonalCopyOfACorporationRowGivesWay() {
        List<String> merged = CorporationService.merged(List.of("1 mine", "2 personal copy"),
                List.of("2 corporation"), row -> Long.parseLong(row.substring(0, 1)));

        assertEquals(List.of("1 mine", "2 corporation"), merged);
    }

    @Test
    void contractItemsAreFetchedTheFirstTimeTheContractIsOpened() {
        service.refresh(characters(), new ArrayList<>());
        market.saveContracts(CORPORATION, List.of(new ContractEntry(7, "item_exchange", "outstanding", "", null, 1.0,
                null, 1.0, "2026-11-27T10:00:00Z", null, null, true, 1, null, null, null, null)));
        when(api.contractItems(CORPORATION, 7, "token")).thenReturn(List.of(new ContractItemDto(1, 34, 500, null,
                true)));

        assertEquals(500, service.contractItems(CORPORATION, 7).getFirst().quantity());
        assertEquals(500, service.contractItems(CORPORATION, 7).getFirst().quantity());
        verify(api).contractItems(CORPORATION, 7, "token");
    }
}
