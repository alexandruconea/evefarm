package com.evefarm.service;

import com.evefarm.auth.AuthService;
import com.evefarm.db.dao.OrderCompetitionDao;
import com.evefarm.esi.MarketsApi;
import com.evefarm.esi.dto.MarketOrderDto;
import com.evefarm.model.EveCharacter;
import com.evefarm.model.OrderCompetition;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;
import java.util.stream.DoubleStream;

public final class MarketWatchService {

    private static final Logger LOG = Logger.getLogger(MarketWatchService.class.getName());
    private static final double PRICE_STEP = 0.005;
    private static final Duration RESULTS_KEPT = Duration.ofHours(1);

    public record Outbid(MarketOrderDto order, double bestPrice) {
    }

    private final AuthService authService;
    private final MarketsApi marketsApi;
    private final OrderCompetitionDao orderCompetitionDao;
    private final Clock clock;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public MarketWatchService(AuthService authService, MarketsApi marketsApi, OrderCompetitionDao orderCompetitionDao) {
        this(authService, marketsApi, orderCompetitionDao, Clock.systemUTC());
    }

    MarketWatchService(AuthService authService, MarketsApi marketsApi, OrderCompetitionDao orderCompetitionDao,
                       Clock clock) {
        this.authService = authService;
        this.marketsApi = marketsApi;
        this.orderCompetitionDao = orderCompetitionDao;
        this.clock = clock;
    }

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public Map<EveCharacter, List<Outbid>> check(List<EveCharacter> characters) {
        Map<EveCharacter, List<MarketOrderDto>> ordersByCharacter = new LinkedHashMap<>();
        Set<Long> ownOrderIds = new HashSet<>();
        for (EveCharacter character : characters) {
            try {
                String token = authService.getValidAccessToken(character.characterId());
                List<MarketOrderDto> orders = marketsApi.listCharacterOrders(character.characterId(), token);
                ordersByCharacter.put(character, orders);
                orders.forEach(order -> ownOrderIds.add(order.orderId()));
            } catch (RuntimeException e) {
                failed(character, e);
            }
        }
        Map<String, List<MarketOrderDto>> markets = new HashMap<>();
        Map<EveCharacter, List<Outbid>> outbid = new LinkedHashMap<>();
        ordersByCharacter.forEach((character, orders) -> {
            try {
                Instant now = clock.instant();
                List<OrderCompetition> competition = new ArrayList<>();
                List<Outbid> beaten = new ArrayList<>();
                for (MarketOrderDto order : orders) {
                    if (order.regionId() == null) {
                        continue;
                    }
                    List<MarketOrderDto> market = markets.computeIfAbsent(order.regionId() + ":" + order.typeId(),
                            key -> marketsApi.listRegionOrders(order.regionId(), order.typeId()));
                    OptionalDouble best = bestCompetitor(order, market, ownOrderIds);
                    boolean isOutbid = best.isPresent() && beats(order, best.getAsDouble());
                    competition.add(new OrderCompetition(order.orderId(),
                            best.isPresent() ? best.getAsDouble() : null, isOutbid, now));
                    if (isOutbid) {
                        beaten.add(new Outbid(order, best.getAsDouble()));
                    }
                }
                orderCompetitionDao.replaceForCharacter(character.characterId(), competition);
                outbid.put(character, beaten);
            } catch (RuntimeException e) {
                failed(character, e);
            }
        });
        if (!outbid.isEmpty()) {
            listeners.forEach(Runnable::run);
        }
        return outbid;
    }

    public Map<Long, OrderCompetition> latestResults() {
        return orderCompetitionDao.findCheckedSince(clock.instant().minus(RESULTS_KEPT));
    }

    static OptionalDouble bestCompetitor(MarketOrderDto mine, List<MarketOrderDto> market, Set<Long> ownOrderIds) {
        DoubleStream prices = market.stream()
                .filter(other -> other.isBuyOrder() == mine.isBuyOrder() && other.locationId() == mine.locationId())
                .filter(other -> !ownOrderIds.contains(other.orderId()))
                .mapToDouble(MarketOrderDto::price);
        return mine.isBuyOrder() ? prices.max() : prices.min();
    }

    static boolean beats(MarketOrderDto mine, double bestPrice) {
        return mine.isBuyOrder() ? bestPrice > mine.price() + PRICE_STEP : bestPrice < mine.price() - PRICE_STEP;
    }

    private static void failed(EveCharacter character, RuntimeException e) {
        LOG.warning("Couldn't compare the market orders of " + character.characterName()
                + " with the other orders at their stations: " + e.getMessage());
    }
}
