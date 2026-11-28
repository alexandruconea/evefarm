package com.evefarm.service;

import com.evefarm.auth.AuthService;
import com.evefarm.auth.LoginExpiredException;
import com.evefarm.auth.OAuthConfig;
import com.evefarm.db.dao.CorporationDao;
import com.evefarm.db.dao.CorporationIndustryDao;
import com.evefarm.db.dao.CorporationMarketDao;
import com.evefarm.esi.CorporationApi;
import com.evefarm.esi.EsiException;
import com.evefarm.esi.dto.AffiliationDto;
import com.evefarm.esi.dto.AssetDto;
import com.evefarm.esi.dto.BlueprintDto;
import com.evefarm.esi.dto.ContractDto;
import com.evefarm.esi.dto.CorporationDivisionsDto;
import com.evefarm.esi.dto.CorporationInfoDto;
import com.evefarm.esi.dto.CorporationWalletDto;
import com.evefarm.esi.dto.IndustryJobDto;
import com.evefarm.esi.dto.MarketOrderDto;
import com.evefarm.esi.dto.WalletJournalEntryDto;
import com.evefarm.model.AssetEntry;
import com.evefarm.model.AssetRow;
import com.evefarm.model.BlueprintRow;
import com.evefarm.model.ContractItemEntry;
import com.evefarm.model.ContractRow;
import com.evefarm.model.Corporation;
import com.evefarm.model.CorporationAccess;
import com.evefarm.model.EveCharacter;
import com.evefarm.model.IndustryJobRow;
import com.evefarm.model.JournalRow;
import com.evefarm.model.MarketOrderRow;
import com.evefarm.model.TransactionRow;
import com.evefarm.model.ValueSummary;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class CorporationService {

    private static final Logger LOG = Logger.getLogger(CorporationService.class.getName());
    private static final int FORBIDDEN = 403;
    private static final String CATEGORY_SHIP = "Ship";
    private static final String CATEGORY_MODULE = "Module";

    public record Card(Corporation corporation, ValueSummary summary, List<CorporationDao.Wallet> wallets) {
    }

    record Reader(EveCharacter character, CorporationAccess access, String accessToken) {
    }

    @FunctionalInterface
    interface Work {
        void run() throws Exception;
    }

    private final AuthService authService;
    private final CorporationApi corporationApi;
    private final CorporationDao corporationDao;
    private final CorporationMarketDao marketDao;
    private final CorporationIndustryDao industryDao;
    private final AssetService assetService;
    private final TransactionService transactionService;
    private final EntityNameCacheService entityNameCacheService;
    private final TypeNameCacheService typeNameCacheService;
    private final LocationNameCacheService locationNameCacheService;
    private final Clock clock;

    public CorporationService(AuthService authService, CorporationApi corporationApi, CorporationDao corporationDao,
                              CorporationMarketDao marketDao, CorporationIndustryDao industryDao,
                              AssetService assetService, TransactionService transactionService,
                              EntityNameCacheService entityNameCacheService, TypeNameCacheService typeNameCacheService,
                              LocationNameCacheService locationNameCacheService, Clock clock) {
        this.authService = authService;
        this.corporationApi = corporationApi;
        this.corporationDao = corporationDao;
        this.marketDao = marketDao;
        this.industryDao = industryDao;
        this.assetService = assetService;
        this.transactionService = transactionService;
        this.entityNameCacheService = entityNameCacheService;
        this.typeNameCacheService = typeNameCacheService;
        this.locationNameCacheService = locationNameCacheService;
        this.clock = clock;
    }

    public static boolean reads(EveCharacter character) {
        return character.scopes() != null && character.scopes().contains(OAuthConfig.CORPORATION_ROLES_SCOPE);
    }

    static boolean has(Reader reader, String scope) {
        return reader.character().scopes() != null && reader.character().scopes().contains(scope);
    }

    static Optional<Reader> readerFor(List<Reader> readers, Predicate<CorporationAccess> role, String scope) {
        return readers.stream().filter(reader -> role.test(reader.access()) && has(reader, scope)).findFirst();
    }

    public void refresh(List<EveCharacter> characters, List<String> failures) {
        List<EveCharacter> asked = characters.stream().filter(CorporationService::reads).toList();
        if (asked.isEmpty()) {
            return;
        }
        Map<Long, Long> corporationOf = new HashMap<>();
        try {
            for (AffiliationDto affiliation : corporationApi.affiliations(
                    asked.stream().map(EveCharacter::characterId).toList())) {
                corporationOf.put(affiliation.characterId(), affiliation.corporationId());
            }
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Failed to find the corporations of the characters", e);
            failures.add("Corporations: " + e.getMessage());
            return;
        }
        Map<Long, CorporationInfoDto> infos = new HashMap<>();
        Map<Long, List<Reader>> byCorporation = new LinkedHashMap<>();
        for (EveCharacter character : asked) {
            Long corporationId = corporationOf.get(character.characterId());
            if (corporationId == null) {
                continue;
            }
            try {
                String token = authService.getValidAccessToken(character.characterId());
                CorporationInfoDto info = infos.computeIfAbsent(corporationId, corporationApi::corporation);
                List<String> roles = new ArrayList<>(corporationApi.roles(character.characterId(), token));
                if (info.ceoId() != null && info.ceoId() == character.characterId()
                        && !roles.contains(CorporationAccess.DIRECTOR)) {
                    roles.add(CorporationAccess.DIRECTOR);
                }
                CorporationAccess access = new CorporationAccess(character.characterId(), corporationId, roles,
                        clock.instant());
                corporationDao.saveAccess(access);
                byCorporation.computeIfAbsent(corporationId, id -> new ArrayList<>())
                        .add(new Reader(character, access, token));
            } catch (LoginExpiredException e) {
                failures.add(e.getMessage());
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Failed to read the corporation roles of " + character.characterName(), e);
                failures.add(character.characterName() + " (corporation roles): " + e.getMessage());
            }
        }
        byCorporation.forEach((corporationId, readers) -> {
            CorporationInfoDto info = infos.get(corporationId);
            Corporation corporation = new Corporation(corporationId, info.name(), info.ticker());
            corporationDao.saveCorporation(corporation);
            readerFor(readers, CorporationAccess::readsWallets, OAuthConfig.CORPORATION_WALLETS_SCOPE).ifPresent(
                    reader -> attempt(failures, corporation, "wallets",
                            () -> refreshWallets(corporationId, reader.accessToken())));
            readerFor(readers, CorporationAccess::readsAssets, OAuthConfig.CORPORATION_ASSETS_SCOPE).ifPresent(
                    reader -> attempt(failures, corporation, "assets", () -> {
                        if (has(reader, OAuthConfig.CORPORATION_DIVISIONS_SCOPE)) {
                            refreshDivisions(corporationId, reader.accessToken());
                        }
                        refreshAssets(corporationId, reader.accessToken());
                    }));
            readerFor(readers, CorporationAccess::readsOrders, OAuthConfig.CORPORATION_ORDERS_SCOPE).ifPresent(
                    reader -> attempt(failures, corporation, "market orders",
                            () -> refreshOrders(corporationId, reader.accessToken())));
            readerFor(readers, access -> true, OAuthConfig.CORPORATION_CONTRACTS_SCOPE).ifPresent(
                    reader -> attempt(failures, corporation, "contracts",
                            () -> refreshContracts(corporationId, reader.accessToken())));
            readerFor(readers, CorporationAccess::readsJobs, OAuthConfig.CORPORATION_JOBS_SCOPE).ifPresent(
                    reader -> attempt(failures, corporation, "industry jobs",
                            () -> refreshJobs(corporationId, reader.accessToken())));
            readerFor(readers, CorporationAccess::readsAssets, OAuthConfig.CORPORATION_BLUEPRINTS_SCOPE).ifPresent(
                    reader -> attempt(failures, corporation, "blueprints",
                            () -> refreshBlueprints(corporationId, reader.accessToken())));
        });
    }

    private void refreshOrders(long corporationId, String token) {
        List<MarketOrderDto> active = corporationApi.orders(corporationId, token);
        List<MarketOrderDto> history = corporationApi.orderHistory(corporationId, token);
        List<MarketOrderDto> all = Stream.concat(active.stream(), history.stream()).toList();
        typeNameCacheService.resolveTypes(all.stream().map(MarketOrderDto::typeId).collect(Collectors.toSet()));
        locationNameCacheService.resolveLocations(
                all.stream().map(MarketOrderDto::locationId).collect(Collectors.toSet()), token);
        List<CorporationMarketDao.Order> orders = new ArrayList<>(all.size());
        active.forEach(order -> orders.add(new CorporationMarketDao.Order(
                MarketOrderService.entry(order, MarketOrderRow.ACTIVE), order.walletDivision())));
        history.forEach(order -> orders.add(new CorporationMarketDao.Order(
                MarketOrderService.entry(order, order.state() == null ? MarketOrderRow.CLOSED : order.state()),
                order.walletDivision())));
        marketDao.saveOrders(corporationId, orders);
    }

    private void refreshContracts(long corporationId, String token) {
        List<ContractDto> contracts = corporationApi.contracts(corporationId, token);
        entityNameCacheService.resolveEntities(ContractService.parties(contracts));
        locationNameCacheService.resolveLocations(ContractService.locations(contracts), token);
        marketDao.saveContracts(corporationId, contracts.stream().map(ContractService::entry).toList());
    }

    private void refreshJobs(long corporationId, String token) {
        List<IndustryJobDto> jobs = corporationApi.industryJobs(corporationId, token);
        typeNameCacheService.resolveTypes(IndustryJobService.typeIds(jobs));
        locationNameCacheService.resolveLocations(IndustryJobService.facilityIds(jobs), token);
        entityNameCacheService.resolveEntities(jobs.stream().map(IndustryJobDto::installerId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        industryDao.saveJobs(corporationId, jobs.stream()
                .map(job -> new CorporationIndustryDao.Job(IndustryJobService.entry(job), job.installerId()))
                .toList());
    }

    private void refreshBlueprints(long corporationId, String token) {
        List<BlueprintDto> blueprints = corporationApi.blueprints(corporationId, token);
        typeNameCacheService.resolveTypes(blueprints.stream().map(BlueprintDto::typeId).collect(Collectors.toSet()));
        industryDao.replaceBlueprints(corporationId, blueprints.stream().map(BlueprintService::entry).toList());
    }

    private static void attempt(List<String> failures, Corporation corporation, String what, Work work) {
        try {
            work.run();
        } catch (EsiException e) {
            if (e.statusCode() == FORBIDDEN) {
                LOG.info("No access to the " + what + " of " + corporation.label() + ": " + e.getMessage());
            } else {
                LOG.log(Level.WARNING, "Failed to read the " + what + " of " + corporation.label(), e);
                failures.add(corporation.label() + " (" + what + "): " + e.getMessage());
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to read the " + what + " of " + corporation.label(), e);
            failures.add(corporation.label() + " (" + what + "): " + e.getMessage());
        }
    }

    private void refreshWallets(long corporationId, String token) {
        List<CorporationWalletDto> wallets = corporationApi.wallets(corporationId, token);
        Map<Integer, Double> balances = new LinkedHashMap<>();
        wallets.forEach(wallet -> balances.put(wallet.division(), wallet.balance()));
        corporationDao.saveWallets(corporationId, balances);
        for (CorporationWalletDto wallet : wallets) {
            List<WalletJournalEntryDto> journal = corporationApi.journal(corporationId, wallet.division(), token);
            entityNameCacheService.resolveEntities(JournalService.parties(journal));
            corporationDao.saveJournal(corporationId, wallet.division(), JournalService.entries(journal));
            corporationDao.saveTransactions(corporationId, wallet.division(), transactionService.prepare(
                    corporationApi.transactions(corporationId, wallet.division(), token), token));
        }
    }

    private void refreshDivisions(long corporationId, String token) {
        CorporationDivisionsDto divisions = corporationApi.divisions(corporationId, token);
        corporationDao.saveDivisions(corporationId, CorporationDao.HANGAR, names(divisions.hangar()));
        corporationDao.saveDivisions(corporationId, CorporationDao.WALLET, names(divisions.wallet()));
    }

    private static Map<Integer, String> names(List<CorporationDivisionsDto.Division> divisions) {
        Map<Integer, String> names = new LinkedHashMap<>();
        if (divisions != null) {
            divisions.forEach(division -> names.put(division.division(), division.name()));
        }
        return names;
    }

    private void refreshAssets(long corporationId, String token) {
        List<AssetDto> assets = AssetService.distinct(corporationApi.assets(corporationId, token));
        Map<Long, String> customNames = AssetService.fetchCustomNames("corporation " + corporationId, assets,
                ids -> corporationApi.assetNames(corporationId, token, ids),
                () -> corporationDao.assetNames(corporationId));
        List<AssetEntry> entries = assetService.entries(assets, customNames, token);
        corporationDao.replaceAssets(corporationId, entries);
    }

    public List<AssetRow> assetRows() {
        return corporationDao.listAssetRows();
    }

    public List<JournalRow> journalRows() {
        return corporationDao.listJournalRows();
    }

    public List<TransactionRow> transactionRows() {
        return corporationDao.listTransactionRows();
    }

    public List<MarketOrderRow> orderRows(boolean includeClosed) {
        return marketDao.listOrderRows(includeClosed);
    }

    public List<ContractRow> contractRows() {
        return marketDao.listContractRows();
    }

    public List<IndustryJobRow> jobRows() {
        return industryDao.listJobRows();
    }

    public List<BlueprintRow> blueprintRows() {
        return industryDao.listBlueprintRows();
    }

    public static <T> List<T> merged(List<T> personal, List<T> corporation, ToLongFunction<T> id) {
        Set<Long> corporationIds = corporation.stream().map(id::applyAsLong).collect(Collectors.toSet());
        return Stream.concat(personal.stream().filter(row -> !corporationIds.contains(id.applyAsLong(row))),
                corporation.stream()).toList();
    }

    public List<ContractItemEntry> contractItems(long corporationId, long contractId) {
        Optional<List<ContractItemEntry>> saved = marketDao.findContractItems(corporationId, contractId);
        if (saved.isPresent()) {
            return saved.get();
        }
        RuntimeException refused = new IllegalStateException(
                "None of your characters can read the contracts of this corporation");
        for (CorporationAccess access : corporationDao.listAccess()) {
            if (access.corporationId() != corporationId) {
                continue;
            }
            try {
                String token = authService.getValidAccessToken(access.characterId());
                List<ContractItemEntry> items = corporationApi.contractItems(corporationId, contractId, token)
                        .stream().map(ContractService::itemEntry).toList();
                marketDao.saveContractItems(corporationId, contractId, items);
                return items;
            } catch (EsiException e) {
                if (e.statusCode() != FORBIDDEN) {
                    throw e;
                }
                refused = e;
            } catch (LoginExpiredException e) {
                refused = e;
            }
        }
        throw refused;
    }

    public Map<Long, CorporationAccess> accessByCharacter() {
        Map<Long, CorporationAccess> access = new HashMap<>();
        corporationDao.listAccess().forEach(entry -> access.put(entry.characterId(), entry));
        return access;
    }

    public Map<Long, Corporation> corporations() {
        Map<Long, Corporation> corporations = new LinkedHashMap<>();
        corporationDao.listCorporations().forEach(corporation ->
                corporations.put(corporation.corporationId(), corporation));
        return corporations;
    }

    public List<Card> cards() {
        List<Card> cards = new ArrayList<>();
        for (Corporation corporation : corporationDao.listCorporations()) {
            long id = corporation.corporationId();
            List<CorporationDao.Wallet> wallets = corporationDao.wallets(id);
            double walletTotal = corporationDao.walletsValue(id);
            double assets = corporationDao.assetsValue(id);
            double sellOrders = marketDao.sellOrdersValue(id);
            if (wallets.isEmpty() && assets == 0 && sellOrders == 0) {
                continue;
            }
            Optional<AssetRow> best = corporationDao.mostValuable(id, null);
            Optional<AssetRow> ship = corporationDao.mostValuable(id, CATEGORY_SHIP);
            Optional<AssetRow> module = corporationDao.mostValuable(id, CATEGORY_MODULE);
            cards.add(new Card(corporation, new ValueSummary(walletTotal + assets + sellOrders, walletTotal, assets,
                    sellOrders, null,
                    best.map(AssetRow::typeName).orElse(null), best.map(AssetRow::unitPrice).orElse(0.0),
                    ship.map(AssetRow::typeName).orElse(null), ship.map(AssetRow::unitPrice).orElse(0.0),
                    module.map(AssetRow::typeName).orElse(null), module.map(AssetRow::unitPrice).orElse(0.0)),
                    wallets));
        }
        return cards;
    }
}
