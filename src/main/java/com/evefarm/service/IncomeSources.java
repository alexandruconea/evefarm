package com.evefarm.service;

import com.evefarm.db.dao.AbyssalRunDao;
import com.evefarm.db.dao.IndustryJobDao;
import com.evefarm.db.dao.IndustryJobDao.FinishedJob;
import com.evefarm.db.dao.LpPurchaseDao;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.db.dao.WalletJournalDao;
import com.evefarm.db.dao.WalletJournalDao.MissionIncomeEntry;
import com.evefarm.db.dao.WalletTransactionDao;
import com.evefarm.model.AbyssalRun;
import com.evefarm.model.IndustryActivity;
import com.evefarm.model.LpGain;
import com.evefarm.model.LpPurchase;
import com.evefarm.model.MiningRow;
import com.evefarm.model.TypeQuantity;
import com.evefarm.service.LpPurchaseService.Allocation;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class IncomeSources {

    private static final Logger LOG = Logger.getLogger(IncomeSources.class.getName());
    static final Set<String> MARKET_FEES = Set.of("brokers_fee", "transaction_tax");
    private static final int JOB_MANUFACTURING = 1;
    private static final int JOB_REACTION = 9;

    public enum Source {
        MISSIONS("Missions", "Mission rewards, time bonuses and the bounties of mission NPCs, from your wallet "
                + "journal"),
        BOUNTIES("Bounties", "Bounties far from any mission turn-in, such as ratting"),
        MARKET("Market", "What you sold on the market less what you bought, the broker fees and the sales tax. "
                + "Items from the LP store recorded in LP Store > Purchases count under LP Sales instead"),
        INDUSTRY("Industry", "What your finished manufacturing and reaction jobs made, at today's prices"),
        MINING("Mining", "The value of everything mined, valued as chosen in Industry > Mining"),
        LP("LP Earned", "The LP earned, at your Set ISK/LP Target..."),
        LP_SALES("LP Sales", "What items from the LP store sold for, less the ISK paid in the store and their LP "
                + "at your Set ISK/LP Target...: what they made beyond LP Earned. Only the purchases recorded in "
                + "LP Store > Purchases"),
        ABYSS("Abyss", "The profit of your Abyss runs: the loot less the filament");

        private final String label;
        private final String description;

        Source(String label, String description) {
            this.label = label;
            this.description = description;
        }

        public String label() {
            return label;
        }

        public String description() {
            return description;
        }

        public static Optional<Source> byLabel(String label) {
            return Arrays.stream(values()).filter(source -> source.label.equals(label)).findFirst();
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final WalletJournalDao walletJournalDao;
    private final WalletTransactionDao walletTransactionDao;
    private final IndustryJobDao industryJobDao;
    private final IndustryCatalogService industryCatalogService;
    private final PriceService priceService;
    private final MiningService miningService;
    private final LoyaltyPointService loyaltyPointService;
    private final AbyssalRunDao abyssalRunDao;
    private final LpPurchaseDao lpPurchaseDao;
    private final NpcCatalogService npcCatalogService;
    private final SettingsDao settingsDao;
    private final Clock clock;

    public IncomeSources(WalletJournalDao walletJournalDao, WalletTransactionDao walletTransactionDao,
                         IndustryJobDao industryJobDao, IndustryCatalogService industryCatalogService,
                         PriceService priceService, MiningService miningService,
                         LoyaltyPointService loyaltyPointService, AbyssalRunDao abyssalRunDao,
                         LpPurchaseDao lpPurchaseDao,
                         NpcCatalogService npcCatalogService, SettingsDao settingsDao, Clock clock) {
        this.walletJournalDao = walletJournalDao;
        this.walletTransactionDao = walletTransactionDao;
        this.industryJobDao = industryJobDao;
        this.industryCatalogService = industryCatalogService;
        this.priceService = priceService;
        this.miningService = miningService;
        this.loyaltyPointService = loyaltyPointService;
        this.abyssalRunDao = abyssalRunDao;
        this.lpPurchaseDao = lpPurchaseDao;
        this.npcCatalogService = npcCatalogService;
        this.settingsDao = settingsDao;
        this.clock = clock;
    }

    public Map<Source, NavigableMap<LocalDate, Double>> daily(Set<Source> sources, Set<Long> characterIds,
                                                              Instant since) {
        Map<Source, NavigableMap<LocalDate, Double>> bySource = new EnumMap<>(Source.class);
        for (Source source : sources) {
            bySource.put(source, new TreeMap<>());
        }
        if (characterIds.isEmpty() || sources.isEmpty()) {
            return bySource;
        }
        ZoneId zone = clock.getZone();
        if (sources.contains(Source.MISSIONS) || sources.contains(Source.BOUNTIES)) {
            safely("the mission income", () -> addMissionIncome(bySource, characterIds, since, zone));
        }
        List<Allocation> lpSales = sources.contains(Source.MARKET) || sources.contains(Source.LP_SALES)
                ? lpSales() : List.of();
        add(bySource, Source.MARKET, days -> addMarket(days, characterIds, since, zone, lpSales));
        add(bySource, Source.INDUSTRY, days -> addIndustry(days, characterIds, since, zone));
        add(bySource, Source.MINING, days -> addMining(days, characterIds, since, zone));
        add(bySource, Source.LP, days -> addLp(days, characterIds, since, zone));
        add(bySource, Source.LP_SALES, days -> addLpSales(days, characterIds, since, zone, lpSales));
        add(bySource, Source.ABYSS, days -> addAbyss(days, characterIds, since, zone));
        return bySource;
    }

    private List<Allocation> lpSales() {
        try {
            List<LpPurchase> purchases = lpPurchaseDao.listAll();
            if (purchases.isEmpty()) {
                return List.of();
            }
            Set<Integer> typeIds = purchases.stream().map(LpPurchase::typeId).collect(Collectors.toSet());
            return LpPurchaseService.allocate(purchases, walletTransactionDao.findSales(typeIds));
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Couldn't match the LP Store purchases for the forecast", e);
            return List.of();
        }
    }

    private static void add(Map<Source, NavigableMap<LocalDate, Double>> bySource, Source source,
                            Consumer<NavigableMap<LocalDate, Double>> work) {
        NavigableMap<LocalDate, Double> days = bySource.get(source);
        if (days != null) {
            safely(source.label(), () -> work.accept(days));
        }
    }

    private static void safely(String what, Runnable work) {
        try {
            work.run();
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Couldn't add up " + what + " for the forecast", e);
        }
    }

    private void addMissionIncome(Map<Source, NavigableMap<LocalDate, Double>> bySource, Set<Long> characterIds,
                                  Instant since, ZoneId zone) {
        List<MissionIncomeEntry> entries = walletJournalDao.findMissionIncome(since).stream()
                .filter(entry -> characterIds.contains(entry.characterId()))
                .toList();
        for (MissionIncome.Payment payment : MissionIncome.classify(entries,
                npcCatalogService.catalog()::isMissionNpc)) {
            Source source = payment.kind() == MissionIncome.Kind.OTHER_BOUNTY ? Source.BOUNTIES : Source.MISSIONS;
            NavigableMap<LocalDate, Double> days = bySource.get(source);
            if (days != null) {
                add(days, payment.paidAt(), payment.amount(), zone);
            }
        }
    }

    private void addMarket(NavigableMap<LocalDate, Double> days, Set<Long> characterIds, Instant since,
                           ZoneId zone, List<Allocation> lpSales) {
        walletTransactionDao.findTrades(characterIds, since)
                .forEach(trade -> add(days, trade.date(), trade.amount(), zone));
        for (long characterId : characterIds) {
            walletJournalDao.findByRefTypes(characterId, MARKET_FEES, since)
                    .forEach(fee -> add(days, Instant.parse(fee.date()), fee.amount(), zone));
        }
        for (Allocation sale : lpSales) {
            if (characterIds.contains(sale.sellerId()) && !sale.date().isBefore(since)) {
                add(days, sale.date(), -sale.units() * sale.price(), zone);
            }
        }
    }

    private void addLpSales(NavigableMap<LocalDate, Double> days, Set<Long> characterIds, Instant since,
                            ZoneId zone, List<Allocation> lpSales) {
        double target = Math.max(0, settingsDao.getDouble(SettingsDao.LP_STORE_TARGET_ISK_PER_LP,
                SettingsDao.DEFAULT_LP_STORE_TARGET_ISK_PER_LP));
        for (Allocation sale : lpSales) {
            LpPurchase purchase = sale.purchase();
            if (characterIds.contains(purchase.characterId()) && !sale.date().isBefore(since)
                    && purchase.units() > 0) {
                double paidPerUnit = (purchase.iskSpent() + purchase.lpSpent() * target) / purchase.units();
                add(days, sale.date(), sale.units() * (sale.price() - paidPerUnit), zone);
            }
        }
    }

    private void addIndustry(NavigableMap<LocalDate, Double> days, Set<Long> characterIds, Instant since,
                             ZoneId zone) {
        for (FinishedJob job : industryJobDao.findFinished(characterIds, since, clock.instant())) {
            if (job.productTypeId() == null
                    || job.activityId() != JOB_MANUFACTURING && job.activityId() != JOB_REACTION) {
                continue;
            }
            double price = priceService.getUnitPrice(job.productTypeId()).orElse(0);
            if (price > 0) {
                add(days, job.endDate(), job.runs() * unitsPerRun(job) * price, zone);
            }
        }
    }

    private long unitsPerRun(FinishedJob job) {
        int activity = job.activityId() == JOB_MANUFACTURING ? IndustryActivity.MANUFACTURING
                : IndustryActivity.REACTION;
        return industryCatalogService.activity(job.blueprintTypeId(), activity)
                .map(IndustryActivity::product)
                .map(TypeQuantity::quantity)
                .filter(quantity -> quantity > 0)
                .orElse(1L);
    }

    private void addMining(NavigableMap<LocalDate, Double> days, Set<Long> characterIds, Instant since,
                           ZoneId zone) {
        MiningService.Valuation valuation = settingsDao.getEnum(SettingsDao.MINING_VALUATION,
                MiningService.Valuation.class).orElse(MiningService.Valuation.ORE);
        double refineRate = Math.clamp(settingsDao.getDouble(SettingsDao.MINING_REFINE_RATE,
                MiningService.DEFAULT_REFINE_RATE), 0, 100) / 100.0;
        LocalDate first = LocalDate.ofInstant(since, zone);
        for (MiningRow row : miningService.getRows(MiningService.Period.ALL, valuation, refineRate)) {
            LocalDate day = LocalDate.parse(row.date());
            if (characterIds.contains(row.characterId()) && row.value() != null && !day.isBefore(first)) {
                days.merge(day, row.value(), Double::sum);
            }
        }
    }

    private void addLp(NavigableMap<LocalDate, Double> days, Set<Long> characterIds, Instant since, ZoneId zone) {
        double target = settingsDao.getDouble(SettingsDao.LP_STORE_TARGET_ISK_PER_LP,
                SettingsDao.DEFAULT_LP_STORE_TARGET_ISK_PER_LP);
        if (target <= 0) {
            return;
        }
        for (LpGain gain : loyaltyPointService.gains()) {
            if (characterIds.contains(gain.characterId()) && !gain.seenAt().isBefore(since)) {
                add(days, gain.seenAt(), gain.points() * target, zone);
            }
        }
    }

    private void addAbyss(NavigableMap<LocalDate, Double> days, Set<Long> characterIds, Instant since,
                          ZoneId zone) {
        for (AbyssalRun run : abyssalRunDao.listRuns()) {
            if (characterIds.contains(run.characterId()) && !run.startedAt().isBefore(since)) {
                add(days, run.startedAt(), run.profit(), zone);
            }
        }
    }

    private static void add(NavigableMap<LocalDate, Double> days, Instant at, double amount, ZoneId zone) {
        days.merge(LocalDate.ofInstant(at, zone), amount, Double::sum);
    }
}
