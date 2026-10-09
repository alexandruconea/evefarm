package com.evefarm.service;

import com.evefarm.auth.AuthService;
import com.evefarm.db.dao.MarketOrderDao;
import com.evefarm.db.dao.WalletJournalDao;
import com.evefarm.esi.MarketsApi;
import com.evefarm.esi.dto.MarketOrderDto;
import com.evefarm.model.MarketOrderEntry;
import com.evefarm.model.MarketOrderRow;
import com.evefarm.model.OrderCompetition;
import com.evefarm.model.PriceMode;

import java.util.ArrayList;
import java.util.HashMap;
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
    private final MarketWatchService marketWatchService;
    private final BrokerFeeMatcher brokerFeeMatcher;

    public MarketOrderService(AuthService authService, MarketsApi marketsApi,
                               TypeNameCacheService typeNameCacheService,
                               LocationNameCacheService locationNameCacheService, MarketOrderDao marketOrderDao,
                               PriceService priceService, MarketWatchService marketWatchService,
                               WalletJournalDao walletJournalDao) {
        this.authService = authService;
        this.marketsApi = marketsApi;
        this.typeNameCacheService = typeNameCacheService;
        this.locationNameCacheService = locationNameCacheService;
        this.marketOrderDao = marketOrderDao;
        this.priceService = priceService;
        this.marketWatchService = marketWatchService;
        this.brokerFeeMatcher = new BrokerFeeMatcher(walletJournalDao);
    }

    public void refreshOrdersForCharacter(long characterId) {
        String accessToken = authService.getValidAccessToken(characterId);
        List<MarketOrderDto> active = marketsApi.listCharacterOrders(characterId, accessToken);
        List<MarketOrderDto> history = marketsApi.listCharacterOrderHistory(characterId, accessToken);
        List<MarketOrderDto> orders = new ArrayList<>(active);
        orders.addAll(history);

        Set<Integer> typeIds = orders.stream().map(MarketOrderDto::typeId).collect(Collectors.toSet());
        typeNameCacheService.resolveTypes(typeIds);

        Set<Long> locationIds = orders.stream().map(MarketOrderDto::locationId).collect(Collectors.toSet());
        locationNameCacheService.resolveLocations(locationIds, accessToken);

        List<MarketOrderEntry> entries = new ArrayList<>(orders.size());
        for (MarketOrderDto order : active) {
            entries.add(entry(order, MarketOrderRow.ACTIVE));
        }
        for (MarketOrderDto order : history) {
            entries.add(entry(order, order.state() == null ? MarketOrderRow.CLOSED : order.state()));
        }
        marketOrderDao.saveForCharacter(characterId, entries);
    }

    private static MarketOrderEntry entry(MarketOrderDto order, String state) {
        return new MarketOrderEntry(order.orderId(), order.typeId(), order.isBuyOrder(), order.price(),
                order.volumeRemain(), order.volumeTotal(), order.escrow(), order.locationId(), order.issued(),
                order.duration(), state, order.range(), order.minVolume());
    }

    public List<MarketOrderRow> getOrderRows(Set<Long> characterIdFilter, boolean includeClosed) {
        Map<Integer, Double> marketPrices = priceService.getUnitPrices();
        Map<Integer, Double> sellMins = priceService.getUnitPrices(PriceMode.SELL_MIN);
        Map<Integer, Double> buyMaxes = priceService.getUnitPrices(PriceMode.BUY_MAX);
        Map<Long, OrderCompetition> competition = marketWatchService.latestResults();
        List<MarketOrderRow> rows = marketOrderDao.listRows(characterIdFilter, includeClosed).stream()
                .map(row -> row.active()
                        ? withMarketPrice(row, marketPrices, sellMins, buyMaxes, competition.get(row.orderId()))
                        : row)
                .toList();
        return withBrokerFees(rows);
    }

    private static MarketOrderRow withMarketPrice(MarketOrderRow row, Map<Integer, Double> marketPrices,
                                                   Map<Integer, Double> sellMins, Map<Integer, Double> buyMaxes,
                                                   OrderCompetition competition) {
        Double marketPrice = marketPrices.get(row.typeId());
        Double sellMin = sellMins.get(row.typeId());
        Double buyMax = buyMaxes.get(row.typeId());
        Double marginPercent = null;
        Double profit = null;
        if (marketPrice != null && marketPrice > 0) {
            profit = row.price() - marketPrice;
            marginPercent = (profit / marketPrice) * 100;
        }
        Boolean outbid = competition == null ? null : competition.outbid();
        Double competitorPrice = competition == null ? null : competition.bestPrice();
        return new MarketOrderRow(row.orderId(), row.characterId(), row.characterName(), row.typeId(),
                row.typeName(), row.groupName(), row.categoryName(), row.isBuyOrder(), row.state(), row.price(),
                row.volumeRemain(), row.volumeTotal(), row.escrow(), row.locationId(), row.locationName(),
                row.issued(), row.duration(), row.range(), row.minVolume(), row.volume(), marketPrice, sellMin,
                buyMax, marginPercent, profit, outbid, competitorPrice, null, null);
    }

    private List<MarketOrderRow> withBrokerFees(List<MarketOrderRow> rows) {
        Map<Long, List<MarketOrderRow>> byCharacter = rows.stream()
                .collect(Collectors.groupingBy(MarketOrderRow::characterId));

        Map<Long, Double> feesByOrderId = new HashMap<>();
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
                    row.typeName(), row.groupName(), row.categoryName(), row.isBuyOrder(), row.state(), row.price(),
                    row.volumeRemain(), row.volumeTotal(), row.escrow(), row.locationId(), row.locationName(),
                    row.issued(), row.duration(), row.range(), row.minVolume(), row.volume(), row.marketPrice(),
                    row.marketSellMin(), row.marketBuyMax(), row.marketMarginPercent(), row.marketProfit(),
                    row.outbid(), row.competitorPrice(), fee, feePercent));
        }
        return result;
    }
}
