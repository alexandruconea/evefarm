package com.evefarm.service;

import com.evefarm.db.dao.AssetDao;
import com.evefarm.db.dao.SnapshotDao;
import com.evefarm.model.AssetRow;
import com.evefarm.model.EveCharacter;
import com.evefarm.model.TrackerSnapshot;
import com.evefarm.model.ValueSummary;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class ValueSummaryService {

    private static final String CATEGORY_SHIP = "Ship";
    private static final String CATEGORY_MODULE = "Module";

    private final SnapshotDao snapshotDao;
    private final AssetDao assetDao;
    private final CharacterService characterService;

    public ValueSummaryService(SnapshotDao snapshotDao, AssetDao assetDao, CharacterService characterService) {
        this.snapshotDao = snapshotDao;
        this.assetDao = assetDao;
        this.characterService = characterService;
    }

    public ValueSummary summarizeCharacter(long characterId) {
        Optional<TrackerSnapshot> latest = snapshotDao.findLatest(characterId);
        return build(latest, findBest(Set.of(characterId)));
    }

    public ValueSummary summarizeGrandTotal() {
        List<EveCharacter> characters = characterService.listCharacters();
        double wallet = 0;
        double assets = 0;
        double sellOrders = 0;
        double escrow = 0;
        double escrowToCover = 0;
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
            escrow += snapshot.escrowValue();
            escrowToCover += snapshot.escrowToCoverValue();
            total += snapshot.totalValue();
        }
        Best best = findBest(null);
        return new ValueSummary(total, wallet, assets, sellOrders, escrow, escrowToCover,
                best.assetName(), best.assetValue(), best.shipName(), best.shipValue(),
                best.moduleName(), best.moduleValue());
    }

    private ValueSummary build(Optional<TrackerSnapshot> latest, Best best) {
        TrackerSnapshot snapshot = latest.orElse(null);
        return new ValueSummary(
                snapshot == null ? 0 : snapshot.totalValue(),
                snapshot == null ? 0 : snapshot.walletBalance(),
                snapshot == null ? 0 : snapshot.assetsValue(),
                snapshot == null ? 0 : snapshot.sellOrdersValue(),
                snapshot == null ? 0 : snapshot.escrowValue(),
                snapshot == null ? 0 : snapshot.escrowToCoverValue(),
                best.assetName(), best.assetValue(),
                best.shipName(), best.shipValue(),
                best.moduleName(), best.moduleValue());
    }

    private record Best(String assetName, double assetValue, String shipName, double shipValue,
                         String moduleName, double moduleValue) {
    }

    private Best findBest(Set<Long> characterIdFilter) {
        List<AssetRow> rows = assetDao.listRows(characterIdFilter);
        AssetRow bestAsset = null;
        AssetRow bestShip = null;
        AssetRow bestModule = null;
        for (AssetRow row : rows) {
            if (bestAsset == null || row.unitPrice() > bestAsset.unitPrice()) {
                bestAsset = row;
            }
            if (CATEGORY_SHIP.equals(row.categoryName()) && (bestShip == null || row.unitPrice() > bestShip.unitPrice())) {
                bestShip = row;
            }
            if (CATEGORY_MODULE.equals(row.categoryName()) && (bestModule == null || row.unitPrice() > bestModule.unitPrice())) {
                bestModule = row;
            }
        }
        return new Best(
                bestAsset == null ? null : bestAsset.typeName(), bestAsset == null ? 0 : bestAsset.unitPrice(),
                bestShip == null ? null : bestShip.typeName(), bestShip == null ? 0 : bestShip.unitPrice(),
                bestModule == null ? null : bestModule.typeName(), bestModule == null ? 0 : bestModule.unitPrice());
    }
}
