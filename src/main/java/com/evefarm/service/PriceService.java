package com.evefarm.service;

import com.evefarm.auth.TokenCipher;
import com.evefarm.db.dao.PriceCacheDao;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.db.dao.TypeCacheDao;
import com.evefarm.esi.FuzzworkApi;
import com.evefarm.esi.JaniceApi;
import com.evefarm.esi.MarketsApi;
import com.evefarm.esi.dto.FuzzworkAggregateDto;
import com.evefarm.esi.dto.JaniceItemDto;
import com.evefarm.esi.dto.MarketPriceDto;
import com.evefarm.model.PriceBreakdown;
import com.evefarm.model.PriceMode;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PriceService {

    private static final Logger LOG = Logger.getLogger(PriceService.class.getName());

    public static final String PROVIDER_CCP = "ccp";
    public static final String PROVIDER_FUZZWORK = "fuzzwork";
    public static final String PROVIDER_JANICE = "janice";

    private final MarketsApi marketsApi;
    private final FuzzworkApi fuzzworkApi;
    private final JaniceApi janiceApi;
    private final PriceCacheDao priceCacheDao;
    private final TypeCacheDao typeCacheDao;
    private final SettingsDao settingsDao;

    public PriceService(MarketsApi marketsApi, FuzzworkApi fuzzworkApi, JaniceApi janiceApi,
                         PriceCacheDao priceCacheDao, TypeCacheDao typeCacheDao, SettingsDao settingsDao) {
        this.marketsApi = marketsApi;
        this.fuzzworkApi = fuzzworkApi;
        this.janiceApi = janiceApi;
        this.priceCacheDao = priceCacheDao;
        this.typeCacheDao = typeCacheDao;
        this.settingsDao = settingsDao;
    }

    public void refreshPrices() {
        String provider = settingsDao.getOrDefault(SettingsDao.PRICE_PROVIDER, PROVIDER_CCP);
        if (PROVIDER_FUZZWORK.equals(provider)) {
            refreshFuzzworkPrices();
        } else if (PROVIDER_JANICE.equals(provider)) {
            refreshJanicePrices();
        } else {
            refreshCcpPrices();
        }
    }

    private void refreshCcpPrices() {
        LOG.info("Refreshing market price cache (CCP)");
        Map<Integer, PriceCacheDao.AverageAdjusted> byType = new HashMap<>();
        for (MarketPriceDto price : marketsApi.listPrices()) {
            byType.put(price.typeId(), new PriceCacheDao.AverageAdjusted(price.averagePrice(), price.adjustedPrice()));
        }
        priceCacheDao.replaceAll(byType);
    }

    private void refreshFuzzworkPrices() {
        refreshFuzzworkPrices(typeCacheDao.listAllTypeIds());
    }

    private void refreshFuzzworkPrices(List<Integer> typeIds) {
        if (typeIds.isEmpty()) {
            LOG.info("Skipping Fuzzwork price refresh - no known item types yet");
            return;
        }
        LOG.info("Refreshing market price cache (Fuzzwork) for " + typeIds.size() + " known types");
        Map<Integer, FuzzworkAggregateDto> aggregates = fuzzworkApi.fetchAggregates(typeIds);
        Map<Integer, PriceBreakdown> byType = new HashMap<>();
        for (Map.Entry<Integer, FuzzworkAggregateDto> entry : aggregates.entrySet()) {
            FuzzworkAggregateDto aggregate = entry.getValue();
            FuzzworkAggregateDto.Side sell = aggregate.sell();
            FuzzworkAggregateDto.Side buy = aggregate.buy();
            byType.put(entry.getKey(), new PriceBreakdown(
                    parsePrice(sell == null ? null : sell.max()),
                    parsePrice(sell == null ? null : sell.weightedAverage()),
                    parsePrice(sell == null ? null : sell.median()),
                    parsePrice(sell == null ? null : sell.percentile()),
                    parsePrice(sell == null ? null : sell.min()),
                    parsePrice(buy == null ? null : buy.max()),
                    parsePrice(buy == null ? null : buy.weightedAverage()),
                    parsePrice(buy == null ? null : buy.median()),
                    parsePrice(buy == null ? null : buy.percentile()),
                    parsePrice(buy == null ? null : buy.min()),
                    parsePrice(sell == null ? null : sell.volume()),
                    parsePrice(buy == null ? null : buy.volume())
            ));
        }
        priceCacheDao.replaceAllDetailed(byType);
    }

    private void refreshJanicePrices() {
        refreshJanicePrices(typeCacheDao.listAllTypeIds());
    }

    private void refreshJanicePrices(List<Integer> typeIds) {
        String apiKey = TokenCipher.decrypt(settingsDao.getOrDefault(SettingsDao.JANICE_API_KEY, ""));
        if (apiKey.isBlank()) {
            throw new IllegalStateException("Janice is selected as price provider, but no API key is configured");
        }
        if (typeIds.isEmpty()) {
            LOG.info("Skipping Janice price refresh - no known item types yet");
            return;
        }
        LOG.info("Refreshing market price cache (Janice) for " + typeIds.size() + " known types");
        Map<Integer, JaniceItemDto> items = janiceApi.fetchPrices(typeIds, apiKey);
        Map<Integer, PriceBreakdown> byType = new HashMap<>();
        for (Map.Entry<Integer, JaniceItemDto> entry : items.entrySet()) {
            JaniceItemDto.Prices immediate = entry.getValue().immediatePrices();
            JaniceItemDto.Prices top5Avg = entry.getValue().top5AveragePrices();
            Double sellLow = immediate == null ? null : immediate.sellPrice();
            Double sellPercentile = top5Avg == null ? null : top5Avg.sellPrice();
            Double buyHigh = immediate == null ? null : immediate.buyPrice();
            Double buyPercentile = top5Avg == null ? null : top5Avg.buyPrice();
            byType.put(entry.getKey(), new PriceBreakdown(
                    null, null, null, sellPercentile, sellLow,
                    buyHigh, null, null, buyPercentile, null,
                    null, null
            ));
        }
        priceCacheDao.replaceAllDetailed(byType);
    }

    private Double parsePrice(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public OptionalDouble getUnitPrice(int typeId) {
        PriceMode mode = resolvePriceMode();
        return priceCacheDao.findUnitPrice(typeId, mode);
    }

    public OptionalDouble getUnitPrice(int typeId, PriceMode mode) {
        return priceCacheDao.findUnitPrice(typeId, mode);
    }

    public Map<Integer, Double> getUnitPrices() {
        return priceCacheDao.findAllUnitPrices(resolvePriceMode());
    }

    public Map<Integer, Double> getUnitPrices(PriceMode mode) {
        return priceCacheDao.findAllUnitPrices(mode);
    }

    public Map<Integer, Double> getSellVolumes() {
        return priceCacheDao.findAllSellVolumes();
    }

    public Map<Integer, Double> getBuyVolumes() {
        return priceCacheDao.findAllBuyVolumes();
    }

    public void ensureFreshPrices(Collection<Integer> typeIds) {
        String provider = settingsDao.getOrDefault(SettingsDao.PRICE_PROVIDER, PROVIDER_CCP);
        if (!PROVIDER_FUZZWORK.equals(provider) && !PROVIDER_JANICE.equals(provider)) {
            return;
        }
        Set<Integer> missing = priceCacheDao.findMissingTypeIds(typeIds);
        if (missing.isEmpty()) {
            return;
        }
        try {
            if (PROVIDER_FUZZWORK.equals(provider)) {
                refreshFuzzworkPrices(List.copyOf(missing));
            } else {
                refreshJanicePrices(List.copyOf(missing));
            }
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Couldn't fetch prices for " + missing.size()
                    + " item types; continuing with the prices already saved", e);
        }
    }

    private PriceMode resolvePriceMode() {
        String name = settingsDao.getOrDefault(SettingsDao.DEFAULT_PRICE_MODE, PriceMode.SELL_AVG.name());
        try {
            return PriceMode.valueOf(name);
        } catch (IllegalArgumentException e) {
            return PriceMode.SELL_AVG;
        }
    }
}
