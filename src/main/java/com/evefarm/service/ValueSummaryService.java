package com.evefarm.service;

import com.evefarm.db.dao.AssetDao;
import com.evefarm.db.dao.LoyaltyPointDao;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.db.dao.SnapshotDao;
import com.evefarm.db.dao.WalletJournalDao;
import com.evefarm.db.dao.WalletJournalDao.MissionIncomeEntry;
import com.evefarm.model.AssetRow;
import com.evefarm.model.EveCharacter;
import com.evefarm.model.LpGain;
import com.evefarm.model.TrackerSnapshot;
import com.evefarm.model.ValueSummary;
import com.evefarm.model.ValueSummary.Today;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToDoubleFunction;

public final class ValueSummaryService {

    private static final String CATEGORY_SHIP = "Ship";
    private static final String CATEGORY_MODULE = "Module";

    private final SnapshotDao snapshotDao;
    private final AssetDao assetDao;
    private final CharacterService characterService;
    private final WalletJournalDao walletJournalDao;
    private final LoyaltyPointDao loyaltyPointDao;
    private final NpcCatalogService npcCatalogService;
    private final SettingsDao settingsDao;
    private final Clock clock;

    public ValueSummaryService(SnapshotDao snapshotDao, AssetDao assetDao, CharacterService characterService,
                               WalletJournalDao walletJournalDao, LoyaltyPointDao loyaltyPointDao,
                               NpcCatalogService npcCatalogService, SettingsDao settingsDao, Clock clock) {
        this.snapshotDao = snapshotDao;
        this.assetDao = assetDao;
        this.characterService = characterService;
        this.walletJournalDao = walletJournalDao;
        this.loyaltyPointDao = loyaltyPointDao;
        this.npcCatalogService = npcCatalogService;
        this.settingsDao = settingsDao;
        this.clock = clock;
    }

    public ValueSummary summarizeCharacter(long characterId) {
        Optional<TrackerSnapshot> latest = snapshotDao.findLatest(characterId);
        return build(latest, today(characterId), findBest(Set.of(characterId)));
    }

    public ValueSummary summarizeGrandTotal() {
        List<EveCharacter> characters = characterService.listCharacters();
        double wallet = 0;
        double assets = 0;
        double sellOrders = 0;
        double total = 0;
        for (EveCharacter character : characters) {
            Optional<TrackerSnapshot> latest = snapshotDao.findLatest(character.characterId());
            if (latest.isEmpty()) {
                continue;
            }
            TrackerSnapshot snapshot = latest.get();
            wallet += snapshot.walletBalance();
            assets += snapshot.assetsValue();
            sellOrders += snapshot.sellOrdersValue();
            total += snapshot.totalValue();
        }
        Best best = findBest(null);
        return new ValueSummary(total, wallet, assets, sellOrders, today(null),
                best.assetName(), best.assetValue(), best.shipName(), best.shipValue(),
                best.moduleName(), best.moduleValue());
    }

    private Today today(Long characterId) {
        ZoneId zone = clock.getZone();
        LocalDate date = LocalDate.now(clock);
        Instant since = date.atStartOfDay(zone).toInstant().minus(MissionIncome.TURN_IN_BEFORE);
        List<MissionIncomeEntry> entries = walletJournalDao.findMissionIncome(since).stream()
                .filter(entry -> characterId == null || entry.characterId() == characterId)
                .toList();
        List<LpGain> gains = loyaltyPointDao.listGains().stream()
                .filter(gain -> characterId == null || gain.characterId() == characterId)
                .toList();
        MissionIncome.Totals totals = MissionIncome.report(
                MissionIncome.classify(entries, npcCatalogService.catalog()::isMissionNpc), gains, date, date, zone)
                .totals();
        return new Today(totals.isk(), totals.lp(), settingsDao.getDouble(SettingsDao.LP_STORE_TARGET_ISK_PER_LP,
                SettingsDao.DEFAULT_LP_STORE_TARGET_ISK_PER_LP));
    }

    private ValueSummary build(Optional<TrackerSnapshot> latest, Today today, Best best) {
        TrackerSnapshot snapshot = latest.orElse(null);
        return new ValueSummary(
                snapshot == null ? 0 : snapshot.totalValue(),
                snapshot == null ? 0 : snapshot.walletBalance(),
                snapshot == null ? 0 : snapshot.assetsValue(),
                snapshot == null ? 0 : snapshot.sellOrdersValue(),
                today,
                best.assetName(), best.assetValue(),
                best.shipName(), best.shipValue(),
                best.moduleName(), best.moduleValue());
    }

    private record Best(String assetName, double assetValue, String shipName, double shipValue,
                         String moduleName, double moduleValue) {
    }

    private Best findBest(Set<Long> characterIdFilter) {
        Optional<AssetRow> asset = assetDao.mostValuable(characterIdFilter, null);
        Optional<AssetRow> ship = assetDao.mostValuable(characterIdFilter, CATEGORY_SHIP);
        Optional<AssetRow> module = assetDao.mostValuable(characterIdFilter, CATEGORY_MODULE);
        ToDoubleFunction<Optional<AssetRow>> price = row -> row.map(AssetRow::unitPrice).orElse(0.0);
        return new Best(asset.map(AssetRow::typeName).orElse(null), price.applyAsDouble(asset),
                ship.map(AssetRow::typeName).orElse(null), price.applyAsDouble(ship),
                module.map(AssetRow::typeName).orElse(null), price.applyAsDouble(module));
    }
}
