package com.evefarm.service;

import com.evefarm.db.dao.CorporationDao;
import com.evefarm.db.dao.CorporationIndustryDao;
import com.evefarm.db.dao.CorporationMarketDao;
import com.evefarm.db.dao.CorporationSnapshotDao;
import com.evefarm.db.dao.IndustryJobDao.FinishedJob;
import com.evefarm.model.Corporation;
import com.evefarm.model.TrackerSnapshot;
import com.evefarm.service.Forecast.Transfer;
import com.evefarm.service.IncomeSources.Source;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The corporations in the Tracker and the Forecast: one snapshot a day of what the corporation owns, and what it
 * earned from its own wallet journal, market transactions and industry jobs.
 */
public final class CorporationTracker {

    private static final Logger LOG = Logger.getLogger(CorporationTracker.class.getName());
    /** Data older than this is not captured again, so a corporation that can't be read any more stops. */
    static final Duration FRESH = Duration.ofDays(1);
    static final Set<String> MISSION_TAX = Set.of(MissionIncome.REWARD, MissionIncome.TIME_BONUS);
    static final Set<String> BOUNTY_TAX = Set.of("bounty_prizes", "ess_escrow_transfer");

    private final CorporationDao corporationDao;
    private final CorporationMarketDao marketDao;
    private final CorporationIndustryDao industryDao;
    private final CorporationSnapshotDao snapshotDao;
    private final IndustryCatalogService industryCatalogService;
    private final PriceService priceService;
    private final Clock clock;

    public CorporationTracker(CorporationDao corporationDao, CorporationMarketDao marketDao,
                              CorporationIndustryDao industryDao, CorporationSnapshotDao snapshotDao,
                              IndustryCatalogService industryCatalogService, PriceService priceService, Clock clock) {
        this.corporationDao = corporationDao;
        this.marketDao = marketDao;
        this.industryDao = industryDao;
        this.snapshotDao = snapshotDao;
        this.industryCatalogService = industryCatalogService;
        this.priceService = priceService;
        this.clock = clock;
    }

    /** Captures today's snapshot of every corporation read in the last day, replacing an earlier one of today. */
    public void capture() {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, clock.getZone());
        for (Corporation corporation : corporationDao.listCorporations()) {
            long id = corporation.corporationId();
            try {
                boolean fresh = corporationDao.lastRead(id).filter(read -> read.isAfter(now.minus(FRESH))).isPresent();
                if (fresh) {
                    snapshotDao.save(snapshot(id, now), today);
                }
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Failed to capture the snapshot of " + corporation.label(), e);
            }
        }
    }

    TrackerSnapshot snapshot(long corporationId, Instant at) {
        return TrackerSnapshot.ofCorporation(corporationId, at, corporationDao.walletsValue(corporationId),
                corporationDao.assetsValue(corporationId), marketDao.sellOrdersValue(corporationId),
                marketDao.escrowValue(corporationId), marketDao.escrowToCoverValue(corporationId),
                manufacturingValue(corporationId), marketDao.contractCollateralValue(corporationId),
                marketDao.contractsValue(corporationId));
    }

    private double manufacturingValue(long corporationId) {
        double total = 0;
        for (FinishedJob job : industryDao.listOpenJobs(corporationId)) {
            if (IndustryCatalogService.jobMakesItems(job.activityId()) && job.productTypeId() != null) {
                total += job.runs() * industryCatalogService.unitsPerRun(job.activityId(), job.blueprintTypeId())
                        * priceService.getUnitPrice(job.productTypeId()).orElse(0);
            }
        }
        return total;
    }

    public List<TrackerSnapshot> snapshots(Set<Long> corporationIds, Instant from, Instant to) {
        return snapshotDao.listBetween(corporationIds, from, to);
    }

    public void delete(TrackerSnapshot snapshot) {
        snapshotDao.delete(snapshot.characterId(), snapshot.capturedAt());
    }

    /** The corporations that have snapshots, by name. */
    public List<Corporation> corporations() {
        Set<Long> tracked = new HashSet<>(snapshotDao.corporationIds());
        return corporationDao.listCorporations().stream()
                .filter(corporation -> tracked.contains(corporation.corporationId()))
                .sorted((left, right) -> left.label().compareToIgnoreCase(right.label()))
                .toList();
    }

    /** ISK moved into or out of the corporation wallets, which is not earned. */
    public List<Transfer> transfers(Set<Long> corporationIds, Instant since) {
        return corporationDao.findJournal(corporationIds, Forecast.TRANSFER_REF_TYPES, since).stream()
                .map(entry -> new Transfer(entry.date(), entry.amount()))
                .toList();
    }

    /**
     * What the corporations earned each day: the corporation tax on missions and bounties, the market trades
     * less their fees, and the finished manufacturing and reaction jobs. The other sources stay empty.
     */
    public Map<Source, NavigableMap<LocalDate, Double>> daily(Set<Source> sources, Set<Long> corporationIds,
                                                              Instant since) {
        Map<Source, NavigableMap<LocalDate, Double>> bySource = new EnumMap<>(Source.class);
        sources.forEach(source -> bySource.put(source, new TreeMap<>()));
        if (corporationIds.isEmpty()) {
            return bySource;
        }
        ZoneId zone = clock.getZone();
        addJournal(bySource.get(Source.MISSIONS), corporationIds, MISSION_TAX, since, zone);
        addJournal(bySource.get(Source.BOUNTIES), corporationIds, BOUNTY_TAX, since, zone);
        NavigableMap<LocalDate, Double> market = bySource.get(Source.MARKET);
        if (market != null) {
            safely(Source.MARKET, () -> {
                corporationDao.findTrades(corporationIds, since)
                        .forEach(trade -> add(market, trade.date(), trade.amount(), zone));
                addJournal(market, corporationIds, IncomeSources.MARKET_FEES, since, zone);
            });
        }
        NavigableMap<LocalDate, Double> industry = bySource.get(Source.INDUSTRY);
        if (industry != null) {
            safely(Source.INDUSTRY, () -> {
                for (FinishedJob job : industryDao.findFinished(corporationIds, since, clock.instant())) {
                    if (!IndustryCatalogService.jobMakesItems(job.activityId()) || job.productTypeId() == null) {
                        continue;
                    }
                    double price = priceService.getUnitPrice(job.productTypeId()).orElse(0);
                    add(industry, job.endDate(), job.runs()
                            * industryCatalogService.unitsPerRun(job.activityId(), job.blueprintTypeId()) * price,
                            zone);
                }
            });
        }
        return bySource;
    }

    private void addJournal(NavigableMap<LocalDate, Double> days, Set<Long> corporationIds, Set<String> refTypes,
                            Instant since, ZoneId zone) {
        if (days != null) {
            safely(null, () -> corporationDao.findJournal(corporationIds, refTypes, since)
                    .forEach(entry -> add(days, entry.date(), entry.amount(), zone)));
        }
    }

    private static void safely(Source source, Runnable work) {
        try {
            work.run();
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Couldn't add up the corporation " + (source == null ? "journal" : source.label())
                    + " for the forecast", e);
        }
    }

    private static void add(NavigableMap<LocalDate, Double> days, Instant at, double amount, ZoneId zone) {
        days.merge(LocalDate.ofInstant(at, zone), amount, Double::sum);
    }
}
