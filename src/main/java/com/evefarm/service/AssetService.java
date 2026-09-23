package com.evefarm.service;

import com.evefarm.auth.AuthService;
import com.evefarm.db.dao.AssetDao;
import com.evefarm.esi.AssetsApi;
import com.evefarm.esi.dto.AssetDto;
import com.evefarm.esi.dto.AssetNameDto;
import com.evefarm.model.AssetEntry;
import com.evefarm.model.AssetRow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class AssetService {

    private static final Logger LOG = Logger.getLogger(AssetService.class.getName());

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
        List<AssetDto> assets = assetsApi.listAssets(characterId, accessToken);

        Map<Long, String> customNames = fetchCustomNames(characterId, accessToken, assets);

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
        double total = 0;
        for (AssetDto asset : assets) {
            double unitPrice = unitPrices.getOrDefault(asset.typeId(), 0.0);
            double totalValue = unitPrice * asset.quantity();
            total += totalValue;
            long displayLocationId = topLevelLocationByItemId.get(asset.itemId());
            entries.add(new AssetEntry(
                    asset.itemId(), asset.typeId(), asset.quantity(), displayLocationId,
                    asset.locationFlag(), asset.isSingleton(), customNames.get(asset.itemId()),
                    containerNameByItemId.get(asset.itemId()), unitPrice, totalValue));
        }

        assetDao.replaceForCharacter(characterId, entries);
        return total;
    }

    public List<AssetRow> getAssetRows(Set<Long> characterIdFilter) {
        return assetDao.listRows(characterIdFilter);
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
        if (parent == null) {
            return null;
        }
        String customName = customNames.get(parent.itemId());
        if (customName != null && !customName.isBlank()) {
            return customName;
        }
        return typeNameCacheService.resolveType(parent.typeId()).name();
    }

    private Map<Long, String> fetchCustomNames(long characterId, String accessToken, List<AssetDto> assets) {
        List<Long> singletonIds = assets.stream()
                .filter(AssetDto::isSingleton)
                .map(AssetDto::itemId)
                .collect(Collectors.toList());
        if (singletonIds.isEmpty()) {
            return Map.of();
        }
        try {
            Map<Long, String> names = new HashMap<>();
            for (int start = 0; start < singletonIds.size(); start += 1000) {
                List<Long> batch = singletonIds.subList(start, Math.min(start + 1000, singletonIds.size()));
                for (AssetNameDto dto : assetsApi.resolveNames(characterId, accessToken, batch)) {
                    names.put(dto.itemId(), dto.name());
                }
            }
            return names;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to resolve custom asset names for character " + characterId, e);
            return Map.of();
        }
    }
}
