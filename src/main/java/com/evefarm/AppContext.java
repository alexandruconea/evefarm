package com.evefarm;

import com.evefarm.auth.AuthService;
import com.evefarm.db.Database;
import com.evefarm.db.dao.AgentDao;
import com.evefarm.db.dao.AssetDao;
import com.evefarm.db.dao.CharacterDao;
import com.evefarm.db.dao.ContractDao;
import com.evefarm.db.dao.EntityNameCacheDao;
import com.evefarm.db.dao.IndustryJobDao;
import com.evefarm.db.dao.EncounterDao;
import com.evefarm.db.dao.ItemTypeDao;
import com.evefarm.db.dao.KillDao;
import com.evefarm.db.dao.NpcTypeDao;
import com.evefarm.db.dao.OfficerDao;
import com.evefarm.db.dao.LocationCacheDao;
import com.evefarm.db.dao.LoyaltyPointDao;
import com.evefarm.db.dao.MarketOrderDao;
import com.evefarm.db.dao.PriceCacheDao;
import com.evefarm.db.dao.SavedFilterDao;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.db.dao.SkillPointFilterDao;
import com.evefarm.db.dao.SnapshotDao;
import com.evefarm.db.dao.TableColumnStateDao;
import com.evefarm.db.dao.TokenDao;
import com.evefarm.db.dao.TypeCacheDao;
import com.evefarm.db.dao.UpdateCooldownDao;
import com.evefarm.db.dao.WalletJournalDao;
import com.evefarm.db.dao.WalletTransactionDao;
import com.evefarm.esi.AssetsApi;
import com.evefarm.esi.ClonesApi;
import com.evefarm.esi.ContractsApi;
import com.evefarm.esi.EsiHttpClient;
import com.evefarm.esi.FuzzworkApi;
import com.evefarm.esi.IndustryApi;
import com.evefarm.esi.JaniceApi;
import com.evefarm.esi.LoyaltyApi;
import com.evefarm.esi.MarketsApi;
import com.evefarm.esi.SkillsApi;
import com.evefarm.esi.UniverseApi;
import com.evefarm.esi.WalletApi;
import com.evefarm.service.AgentImportService;
import com.evefarm.service.AssetService;
import com.evefarm.service.BackupRestoreService;
import com.evefarm.service.CharacterService;
import com.evefarm.service.ContractService;
import com.evefarm.service.EntityNameCacheService;
import com.evefarm.service.EveSettingsService;
import com.evefarm.service.IndustryJobService;
import com.evefarm.service.ItemIconService;
import com.evefarm.service.JournalService;
import com.evefarm.service.BeltKillMilestoneService;
import com.evefarm.service.KillService;
import com.evefarm.service.NpcCatalogService;
import com.evefarm.service.OfficerService;
import com.evefarm.service.KillsReportService;
import com.evefarm.service.LocationNameCacheService;
import com.evefarm.service.LoyaltyPointService;
import com.evefarm.service.LpOfferPricingService;
import com.evefarm.service.MarketOrderService;
import com.evefarm.service.PriceService;
import com.evefarm.service.SchedulerService;
import com.evefarm.service.TrackerSnapshotService;
import com.evefarm.service.TransactionService;
import com.evefarm.service.TypeNameCacheService;
import com.evefarm.service.ValueSummaryService;
import com.evefarm.update.UpdateInstaller;
import com.evefarm.update.UpdateService;

public final class AppContext {

    public final SettingsDao settingsDao;
    public final CharacterDao characterDao;
    public final TokenDao tokenDao;
    public final AssetDao assetDao;
    public final SnapshotDao snapshotDao;
    public final SkillPointFilterDao skillPointFilterDao;
    public final WalletJournalDao walletJournalDao;
    public final MarketOrderDao marketOrderDao;
    public final SavedFilterDao savedFilterDao;
    public final UpdateCooldownDao updateCooldownDao;
    public final WalletTransactionDao walletTransactionDao;
    public final ContractDao contractDao;
    public final IndustryJobDao industryJobDao;
    public final TableColumnStateDao tableColumnStateDao;
    public final LoyaltyPointDao loyaltyPointDao;
    public final EntityNameCacheDao entityNameCacheDao;
    public final KillDao killDao;
    public final AgentDao agentDao;
    public final EncounterDao encounterDao;
    public final OfficerDao officerDao;

    public final AuthService authService;
    public final CharacterService characterService;
    public final PriceService priceService;
    public final TypeNameCacheService typeNameCacheService;
    public final LocationNameCacheService locationNameCacheService;
    public final EntityNameCacheService entityNameCacheService;
    public final AssetService assetService;
    public final TrackerSnapshotService trackerSnapshotService;
    public final JournalService journalService;
    public final MarketOrderService marketOrderService;
    public final TransactionService transactionService;
    public final ContractService contractService;
    public final IndustryJobService industryJobService;
    public final LoyaltyPointService loyaltyPointService;
    public final NpcCatalogService npcCatalogService;
    public final KillService killService;
    public final BeltKillMilestoneService beltKillMilestoneService;
    public final OfficerService officerService;
    public final KillsReportService killsReportService;
    public final AgentImportService agentImportService;
    public final LpOfferPricingService lpOfferPricingService;
    public final ItemIconService itemIconService;
    public final ValueSummaryService valueSummaryService;
    public final SchedulerService schedulerService;
    public final BackupRestoreService backupRestoreService;
    public final EveSettingsService eveSettingsService;
    public final UpdateService updateService;
    public final UpdateInstaller updateInstaller;

    public AppContext(Database database) {
        this.settingsDao = new SettingsDao(database);
        this.characterDao = new CharacterDao(database);
        this.tokenDao = new TokenDao(database);
        TypeCacheDao typeCacheDao = new TypeCacheDao(database);
        LocationCacheDao locationCacheDao = new LocationCacheDao(database);
        PriceCacheDao priceCacheDao = new PriceCacheDao(database);
        this.assetDao = new AssetDao(database);
        this.snapshotDao = new SnapshotDao(database);
        this.skillPointFilterDao = new SkillPointFilterDao(database);
        this.walletJournalDao = new WalletJournalDao(database);
        this.marketOrderDao = new MarketOrderDao(database);
        this.savedFilterDao = new SavedFilterDao(database);
        this.updateCooldownDao = new UpdateCooldownDao(database);
        this.walletTransactionDao = new WalletTransactionDao(database);
        this.contractDao = new ContractDao(database);
        this.industryJobDao = new IndustryJobDao(database);
        this.tableColumnStateDao = new TableColumnStateDao(database);
        this.loyaltyPointDao = new LoyaltyPointDao(database);
        this.entityNameCacheDao = new EntityNameCacheDao(database);
        this.killDao = new KillDao(database);
        this.agentDao = new AgentDao(database);
        this.encounterDao = new EncounterDao(database);
        this.officerDao = new OfficerDao(database);

        this.authService = new AuthService(characterDao, tokenDao);
        this.characterService = new CharacterService(authService, characterDao, settingsDao);

        EsiHttpClient esiHttpClient = new EsiHttpClient();
        AssetsApi assetsApi = new AssetsApi(esiHttpClient);
        WalletApi walletApi = new WalletApi(esiHttpClient);
        ClonesApi clonesApi = new ClonesApi(esiHttpClient);
        MarketsApi marketsApi = new MarketsApi(esiHttpClient);
        ContractsApi contractsApi = new ContractsApi(esiHttpClient);
        IndustryApi industryApi = new IndustryApi(esiHttpClient);
        SkillsApi skillsApi = new SkillsApi(esiHttpClient);
        UniverseApi universeApi = new UniverseApi(esiHttpClient);
        LoyaltyApi loyaltyApi = new LoyaltyApi(esiHttpClient);
        FuzzworkApi fuzzworkApi = new FuzzworkApi();
        JaniceApi janiceApi = new JaniceApi();

        this.priceService = new PriceService(marketsApi, fuzzworkApi, janiceApi, priceCacheDao, typeCacheDao,
                settingsDao);
        this.typeNameCacheService = new TypeNameCacheService(universeApi, typeCacheDao);
        this.locationNameCacheService = new LocationNameCacheService(universeApi, locationCacheDao);
        this.entityNameCacheService = new EntityNameCacheService(universeApi, entityNameCacheDao);
        this.assetService = new AssetService(authService, assetsApi, typeNameCacheService,
                locationNameCacheService, priceService, assetDao);
        this.loyaltyPointService = new LoyaltyPointService(authService, loyaltyApi, entityNameCacheService,
                entityNameCacheDao, loyaltyPointDao);
        ItemTypeDao itemTypeDao = new ItemTypeDao(database);
        this.npcCatalogService = new NpcCatalogService(fuzzworkApi, new NpcTypeDao(database), itemTypeDao,
                settingsDao, killDao);
        this.killService = new KillService(settingsDao, characterDao, killDao, encounterDao, npcCatalogService);
        this.beltKillMilestoneService = new BeltKillMilestoneService(killDao, settingsDao, npcCatalogService);
        this.officerService = new OfficerService(encounterDao, officerDao, walletJournalDao, itemTypeDao,
                npcCatalogService, priceService, universeApi);
        this.killsReportService = new KillsReportService();
        this.agentImportService = new AgentImportService(fuzzworkApi, agentDao, settingsDao);
        this.lpOfferPricingService = new LpOfferPricingService(loyaltyApi, fuzzworkApi, typeNameCacheService,
                priceService);
        this.itemIconService = new ItemIconService();
        this.trackerSnapshotService = new TrackerSnapshotService(authService, walletApi, clonesApi, marketsApi,
                contractsApi, industryApi, skillsApi, loyaltyApi, priceService, lpOfferPricingService, assetDao,
                snapshotDao, skillPointFilterDao, settingsDao);
        this.journalService = new JournalService(authService, walletApi, entityNameCacheService, walletJournalDao);
        this.marketOrderService = new MarketOrderService(authService, marketsApi, typeNameCacheService,
                locationNameCacheService, marketOrderDao, priceService, walletJournalDao);
        this.transactionService = new TransactionService(authService, walletApi, typeNameCacheService,
                locationNameCacheService, entityNameCacheService, walletTransactionDao);
        this.contractService = new ContractService(authService, contractsApi, entityNameCacheService,
                locationNameCacheService, contractDao);
        this.industryJobService = new IndustryJobService(authService, industryApi, typeNameCacheService,
                locationNameCacheService, industryJobDao);
        this.valueSummaryService = new ValueSummaryService(snapshotDao, assetDao, characterService);
        this.backupRestoreService = new BackupRestoreService(database, settingsDao);
        this.eveSettingsService = new EveSettingsService(settingsDao);
        this.updateService = new UpdateService(settingsDao);
        this.updateInstaller = new UpdateInstaller(backupRestoreService);
        this.schedulerService = new SchedulerService(authService, characterService, priceService, assetService,
                trackerSnapshotService, updateCooldownDao, backupRestoreService, killService);
    }
}
