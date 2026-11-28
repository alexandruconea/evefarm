package com.evefarm.service;

import com.evefarm.auth.AuthService;
import com.evefarm.db.dao.AssetDao;
import com.evefarm.esi.AssetsApi;
import com.evefarm.esi.IdLookup;
import com.evefarm.esi.dto.AssetDto;
import com.evefarm.esi.dto.AssetNameDto;
import com.evefarm.model.AssetEntry;
import com.evefarm.model.AssetRow;
import com.evefarm.util.Chunks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class AssetService {

    private static final Logger LOG = Logger.getLogger(AssetService.class.getName());
    private static final String OFFICE_FOLDER = "OfficeFolder";

    private final AuthService authService;
    private final AssetsApi assetsApi;
    private final TypeNameCacheService typeNameCacheService;
    private final LocationNameCacheService locationNameCacheService;
    private final PriceService priceService;
    private final AssetDao assetDao;

    public AssetService(AuthService authService, AssetsApi assetsApi, TypeNameCacheService typeNameCacheService,
                         LocationNameCacheService locationNameCacheService, PriceService priceService,
                         AssetDao assetDao) {
        this.authService = authService;
        this.assetsApi = assetsApi;
        this.typeNameCacheService = typeNameCacheService;
        this.locationNameCacheService = locationNameCacheService;
        this.priceService = priceService;
        this.assetDao = assetDao;
    }

    public double refreshAssetsForCharacter(long characterId) {
        String accessToken = authService.getValidAccessToken(characterId);
        List<AssetDto> assets = distinct(assetsApi.listAssets(characterId, accessToken));
        Map<Long, String> customNames = fetchCustomNames("character " + characterId, assets,
                ids -> assetsApi.resolveNames(characterId, accessToken, ids), () -> assetDao.findNames(characterId));
        List<AssetEntry> entries = entries(assets, customNames, accessToken);
        assetDao.replaceForCharacter(characterId, entries);
        return entries.stream().mapToDouble(AssetEntry::totalValue).sum();
    }

    static List<AssetDto> distinct(List<AssetDto> assets) {
        return List.copyOf(assets.stream()
                .collect(Collectors.toMap(AssetDto::itemId, asset -> asset, (earlier, later) -> later,
                        LinkedHashMap::new))
                .values());
    }

    List<AssetEntry> entries(List<AssetDto> assets, Map<Long, String> customNames, String accessToken) {
        Set<Integer> distinctTypeIds = assets.stream().map(AssetDto::typeId).collect(Collectors.toSet());
        typeNameCacheService.resolveTypes(distinctTypeIds);

        Map<Long, AssetDto> byItemId = assets.stream()
                .collect(Collectors.toMap(AssetDto::itemId, a -> a, (a, b) -> a));
        Map<Long, Long> topLevelLocationByItemId = new HashMap<>();
        for (AssetDto asset : assets) {
            topLevelLocationByItemId.put(asset.itemId(), resolveTopLevelLocationId(asset, byItemId));
        }
        Map<Long, String> containerNameByItemId = new HashMap<>();
        for (AssetDto asset : assets) {
            containerNameByItemId.put(asset.itemId(), resolveContainerName(asset, byItemId, customNames));
        }

        Set<Long> topLevelLocationIds = new HashSet<>(topLevelLocationByItemId.values());
        locationNameCacheService.resolveLocations(topLevelLocationIds, accessToken);

        Map<Integer, Double> unitPrices = priceService.getUnitPrices();

        List<AssetEntry> entries = new ArrayList<>(assets.size());
        for (AssetDto asset : assets) {
            double unitPrice = asset.isBlueprintCopy() ? 0 : unitPrices.getOrDefault(asset.typeId(), 0.0);
            double totalValue = unitPrice * asset.quantity();
            long displayLocationId = topLevelLocationByItemId.get(asset.itemId());
            entries.add(new AssetEntry(
                    asset.itemId(), asset.typeId(), asset.quantity(), displayLocationId,
                    asset.locationFlag(), asset.isSingleton(), asset.isBlueprintCopy(), customNames.get(asset.itemId()),
                    containerNameByItemId.get(asset.itemId()), unitPrice, totalValue));
        }
        return entries;
    }

    public List<AssetRow> getAssetRows() {
        return assetDao.listRows();
    }

    public Map<Integer, Long> usableStock(Long solarSystemId) {
        return assetDao.usableStock(solarSystemId);
    }

    public List<AssetDao.ArchivedMonth> getArchivedMonths() {
        return assetDao.listArchivedMonths();
    }

    public List<AssetRow> getArchivedAssetRows(String month) {
        return assetDao.listArchivedRows(month);
    }

    private long resolveTopLevelLocationId(AssetDto asset, Map<Long, AssetDto> byItemId) {
        AssetDto current = asset;
        for (int depth = 0; depth < 10; depth++) {
            if (!"item".equals(current.locationType())) {
                return current.locationId();
            }
            AssetDto parent = byItemId.get(current.locationId());
            if (parent == null) {
                return current.locationId();
            }
            current = parent;
        }
        return current.locationId();
    }

    private String resolveContainerName(AssetDto asset, Map<Long, AssetDto> byItemId, Map<Long, String> customNames) {
        if (!"item".equals(asset.locationType())) {
            return null;
        }
        AssetDto parent = byItemId.get(asset.locationId());
        if (parent == null || OFFICE_FOLDER.equals(parent.locationFlag())) {
            return null;
        }
        String customName = customNames.get(parent.itemId());
        if (customName != null && !customName.isBlank()) {
            return customName;
        }
        return typeNameCacheService.resolveType(parent.typeId()).name();
    }

    static Map<Long, String> fetchCustomNames(String owner, List<AssetDto> assets,
                                              Function<List<Long>, List<AssetNameDto>> lookup,
                                              Supplier<Map<Long, String>> namesReadLastTime) {
        List<Long> singletonIds = assets.stream()
                .filter(AssetDto::isSingleton)
                .map(AssetDto::itemId)
                .collect(Collectors.toList());
        if (singletonIds.isEmpty()) {
            return Map.of();
        }
        try {
            Map<Long, String> names = new HashMap<>();
            for (List<Long> batch : Chunks.of(singletonIds, 1000)) {
                for (AssetNameDto dto : IdLookup.skippingInvalidIds(batch, lookup)) {
                    names.put(dto.itemId(), dto.name());
                }
            }
            return names;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to resolve custom asset names for " + owner
                    + " - keeping the names read last time", e);
            return namesReadLastTime.get();
        }
    }
}
