package com.evefarm.service;

import com.evefarm.auth.AuthService;
import com.evefarm.db.dao.MarketOrderDao;
import com.evefarm.db.dao.WalletJournalDao;
import com.evefarm.esi.MarketsApi;
import com.evefarm.esi.dto.MarketOrderDto;
import com.evefarm.model.MarketOrderEntry;
import com.evefarm.model.MarketOrderRow;
import com.evefarm.model.PriceMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class MarketOrderService {

    private final AuthService authService;
    private final MarketsApi marketsApi;
    private final TypeNameCacheService typeNameCacheService;
    private final LocationNameCacheService locationNameCacheService;
    private final MarketOrderDao marketOrderDao;
    private final PriceService priceService;
    private final BrokerFeeMatcher brokerFeeMatcher;

    public MarketOrderService(AuthService authService, MarketsApi marketsApi,
                               TypeNameCacheService typeNameCacheService,
                               LocationNameCacheService locationNameCacheService, MarketOrderDao marketOrderDao,
                               PriceService priceService, WalletJournalDao walletJournalDao) {
        this.authService = authService;
        this.marketsApi = marketsApi;
        this.typeNameCacheService = typeNameCacheService;
        this.locationNameCacheService = locationNameCacheService;
        this.marketOrderDao = marketOrderDao;
        this.priceService = priceService;
        this.brokerFeeMatcher = new BrokerFeeMatcher(walletJournalDao);
    }

    public void refreshOrdersForCharacter(long characterId) {
        String accessToken = authService.getValidAccessToken(characterId);
        List<MarketOrderDto> orders = marketsApi.listCharacterOrders(characterId, accessToken);

        Set<Integer> typeIds = orders.stream().map(MarketOrderDto::typeId).collect(Collectors.toSet());
        typeNameCacheService.resolveTypes(typeIds);

        Set<Long> locationIds = orders.stream().map(MarketOrderDto::locationId).collect(Collectors.toSet());
        locationNameCacheService.resolveLocations(locationIds, accessToken);

        List<MarketOrderEntry> entries = orders.stream()
                .map(order -> new MarketOrderEntry(order.orderId(), order.typeId(), order.isBuyOrder(),
                        order.price(), order.volumeRemain(), order.volumeTotal(), order.escrow(),
                        order.locationId(), order.issued(), order.duration(), order.state(),
                        order.range(), order.minVolume()))
                .toList();
        marketOrderDao.replaceForCharacter(characterId, entries);
    }

    public List<MarketOrderRow> getOrderRows(Set<Long> characterIdFilter) {
        Map<Integer, Double> marketPrices = priceService.getUnitPrices();
        Map<Integer, Double> sellMins = priceService.getUnitPrices(PriceMode.SELL_MIN);
        Map<Integer, Double> buyMaxes = priceService.getUnitPrices(PriceMode.BUY_MAX);
        List<MarketOrderRow> rows = marketOrderDao.listRows(characterIdFilter).stream()
                .map(row -> withMarketPrice(row, marketPrices, sellMins, buyMaxes))
                .toList();
        return withBrokerFees(rows);
    }

    private static final double OUTBID_EPSILON = 0.01;

    private MarketOrderRow withMarketPrice(MarketOrderRow row, Map<Integer, Double> marketPrices,
                                            Map<Integer, Double> sellMins, Map<Integer, Double> buyMaxes) {
        Double marketPrice = marketPrices.get(row.typeId());
        Double sellMin = sellMins.get(row.typeId());
        Double buyMax = buyMaxes.get(row.typeId());
        Double marginPercent = null;
        Double profit = null;
        if (marketPrice != null && marketPrice > 0) {
            profit = row.price() - marketPrice;
            marginPercent = (profit / marketPrice) * 100;
        }
        Boolean outbid = computeOutbid(row.isBuyOrder(), row.price(), sellMin, buyMax);
        return new MarketOrderRow(row.orderId(), row.characterId(), row.characterName(), row.typeId(),
                row.typeName(), row.groupName(), row.categoryName(), row.isBuyOrder(), row.price(),
                row.volumeRemain(), row.volumeTotal(), row.escrow(), row.locationName(), row.issued(),
                row.duration(), row.range(), row.minVolume(), row.volume(), marketPrice, sellMin, buyMax,
                marginPercent, profit, outbid, null, null);
    }

    private Boolean computeOutbid(boolean isBuyOrder, double price, Double sellMin, Double buyMax) {
        if (isBuyOrder) {
            return buyMax == null ? null : buyMax > price + OUTBID_EPSILON;
        }
        return sellMin == null ? null : sellMin < price - OUTBID_EPSILON;
    }

    private List<MarketOrderRow> withBrokerFees(List<MarketOrderRow> rows) {
        Map<Long, List<MarketOrderRow>> byCharacter = rows.stream()
                .collect(Collectors.groupingBy(MarketOrderRow::characterId));

        Map<Long, Double> feesByOrderId = new java.util.HashMap<>();
        for (Map.Entry<Long, List<MarketOrderRow>> entry : byCharacter.entrySet()) {
            feesByOrderId.putAll(brokerFeeMatcher.matchFees(entry.getKey(), entry.getValue()));
        }

        List<MarketOrderRow> result = new ArrayList<>(rows.size());
        for (MarketOrderRow row : rows) {
            Double fee = feesByOrderId.get(row.orderId());
            Double feePercent = null;
            double orderValue = row.price() * row.volumeTotal();
            if (fee != null && orderValue > 0) {
                feePercent = (fee / orderValue) * 100;
            }
            result.add(new MarketOrderRow(row.orderId(), row.characterId(), row.characterName(), row.typeId(),
                    row.typeName(), row.groupName(), row.categoryName(), row.isBuyOrder(), row.price(),
                    row.volumeRemain(), row.volumeTotal(), row.escrow(), row.locationName(), row.issued(),
                    row.duration(), row.range(), row.minVolume(), row.volume(), row.marketPrice(),
                    row.marketSellMin(), row.marketBuyMax(), row.marketMarginPercent(), row.marketProfit(),
                    row.outbid(), fee, feePercent));
        }
        return result;
    }
}
