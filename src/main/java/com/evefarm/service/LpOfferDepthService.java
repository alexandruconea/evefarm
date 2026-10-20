package com.evefarm.service;

import com.evefarm.esi.EsiException;
import com.evefarm.esi.MarketsApi;
import com.evefarm.esi.UniverseApi;
import com.evefarm.esi.dto.MarketOrderDto;
import com.evefarm.model.LpOfferRow;
import com.evefarm.model.LpRequiredItem;
import com.evefarm.model.SellingFees;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class LpOfferDepthService {

    public static final long JITA_4_4 = 60_003_760L;
    static final long JITA = 30_000_142L;
    private static final int PARALLEL_REQUESTS = 8;
    private static final int NO_ROUTE = Integer.MAX_VALUE;
    private static final Logger LOG = Logger.getLogger(LpOfferDepthService.class.getName());

    public record JitaOrders(Map<Integer, List<MarketOrderDto>> byType, Map<Long, Integer> jumpsToJita,
                             Instant readAt) {
    }

    record Fill(long units, double isk) {
    }

    public record Purchase(LpRequiredItem item, boolean buildMaterial, long needed, long forSale, Double lowestPrice,
                           long bought, double cost) {

        public boolean enough() {
            return bought >= needed;
        }

        public Double averagePrice() {
            return bought > 0 ? cost / bought : null;
        }

        public Double aboveLowest() {
            return lowestPrice == null ? null : cost - lowestPrice * bought;
        }
    }

    public record Sale(long units, long sold, Double unitPrice, Double profit, Double iskPerLp, Double netProfit,
                       Double netIskPerLp) {

        public boolean enough() {
            return sold >= units;
        }
    }

    public record Result(int times, long lp, double isk, List<Purchase> purchases, Double otherCost, Sale sellOrder,
                         Sale buyOrders) {
    }

    private final MarketsApi marketsApi;
    private final UniverseApi universeApi;
    private final Map<Long, Integer> jumpsToJita = new ConcurrentHashMap<>();

    public LpOfferDepthService(MarketsApi marketsApi, UniverseApi universeApi) {
        this.marketsApi = marketsApi;
        this.universeApi = universeApi;
    }

    public JitaOrders read(LpOfferRow offer) {
        Map<Integer, String> orderTypes = new LinkedHashMap<>();
        for (LpRequiredItem item : items(offer)) {
            orderTypes.put(item.typeId(), "sell");
        }
        orderTypes.put(offer.outputTypeId(), "all");
        Map<Integer, Future<List<MarketOrderDto>>> pending = new LinkedHashMap<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(PARALLEL_REQUESTS)) {
            orderTypes.forEach((typeId, orderType) -> pending.put(typeId,
                    executor.submit(() -> fetch(typeId, orderType))));
        }
        Map<Integer, List<MarketOrderDto>> byType = new HashMap<>();
        pending.forEach((typeId, future) -> {
            if (future.state() == Future.State.FAILED && future.exceptionNow() instanceof RuntimeException e) {
                throw e;
            }
            byType.put(typeId, future.resultNow());
        });
        return new JitaOrders(byType, jumpsToJita(byType.getOrDefault(offer.outputTypeId(), List.of())),
                Instant.now());
    }

    private Map<Long, Integer> jumpsToJita(List<MarketOrderDto> output) {
        Set<Long> systems = output.stream()
                .filter(order -> order.isBuyOrder() && order.systemId() != null && order.systemId() != JITA
                        && jumpRange(order.range()) != null && jumpRange(order.range()) > 0)
                .map(MarketOrderDto::systemId)
                .collect(Collectors.toSet());
        try (ExecutorService executor = Executors.newFixedThreadPool(PARALLEL_REQUESTS)) {
            systems.stream().filter(system -> !jumpsToJita.containsKey(system))
                    .forEach(system -> executor.submit(() -> readJumps(system)));
        }
        return systems.stream().filter(jumpsToJita::containsKey)
                .collect(Collectors.toMap(system -> system, jumpsToJita::get));
    }

    private void readJumps(long systemId) {
        try {
            jumpsToJita.put(systemId, universeApi.jumps(systemId, JITA).orElse(NO_ROUTE));
        } catch (RuntimeException e) {
            LOG.warning("Couldn't read how many jumps system " + systemId + " is from Jita, so its buy orders "
                    + "are left out: " + e.getMessage());
        }
    }

    private List<MarketOrderDto> fetch(int typeId, String orderType) {
        try {
            return marketsApi.listRegionOrders(MarketHistoryService.THE_FORGE, typeId, orderType);
        } catch (EsiException e) {
            if (e.statusCode() == 400 || e.statusCode() == 404 || e.statusCode() == 422) {
                return List.of();
            }
            throw e;
        }
    }

    public static Result price(LpOfferRow offer, int times, JitaOrders orders, SellingFees fees) {
        List<Purchase> purchases = new ArrayList<>();
        Map<Integer, Long> taken = new HashMap<>();
        offer.requiredItems().forEach(item -> purchases.add(purchase(item, false, times, orders, taken)));
        offer.buildMaterials().forEach(item -> purchases.add(purchase(item, true, times, orders, taken)));
        Double otherCost = purchases.stream().allMatch(Purchase::enough)
                ? purchases.stream().mapToDouble(Purchase::cost).sum() : null;

        long lp = offer.lpCost() * times;
        double isk = offer.iskCost() * times;
        long units = offer.outputUnits() * times;
        List<MarketOrderDto> output = orders.byType().getOrDefault(offer.outputTypeId(), List.of());
        List<MarketOrderDto> sellOrders = sellOrders(output);
        Double lowestSell = sellOrders.isEmpty() ? null : sellOrders.getFirst().price();
        Sale sellOrder = sale(units, units, lowestSell, lp, isk, otherCost, fees.salesTax(), fees.brokerFee());
        Fill bought = fill(buyOrders(output, orders.jumpsToJita()), units);
        Sale buyOrders = sale(units, bought.units(), bought.units() > 0 ? bought.isk() / bought.units() : null, lp,
                isk, otherCost, fees.salesTax(), 0);
        return new Result(times, lp, isk, purchases, otherCost, sellOrder, buyOrders);
    }

    private static List<LpRequiredItem> items(LpOfferRow offer) {
        List<LpRequiredItem> items = new ArrayList<>(offer.requiredItems());
        items.addAll(offer.buildMaterials());
        return items;
    }

    private static Purchase purchase(LpRequiredItem item, boolean buildMaterial, int times, JitaOrders orders,
                                     Map<Integer, Long> taken) {
        List<MarketOrderDto> forSale = sellOrders(orders.byType().getOrDefault(item.typeId(), List.of()));
        long needed = item.quantity() * times;
        long before = taken.getOrDefault(item.typeId(), 0L);
        Fill earlier = fill(forSale, before);
        Fill upTo = fill(forSale, before + needed);
        taken.put(item.typeId(), before + needed);
        return new Purchase(item, buildMaterial, needed, forSale.stream().mapToLong(MarketOrderDto::volumeRemain).sum(),
                forSale.isEmpty() ? null : forSale.getFirst().price(), upTo.units() - earlier.units(),
                upTo.isk() - earlier.isk());
    }

    private static Sale sale(long units, long sold, Double unitPrice, long lp, double isk, Double otherCost,
                             double salesTax, double brokerFee) {
        if (unitPrice == null || otherCost == null || sold < units) {
            return new Sale(units, sold, unitPrice, null, null, null, null);
        }
        double profit = units * unitPrice - isk - otherCost;
        double netProfit = IndustryCalculator.sell(units, unitPrice, salesTax, brokerFee).net() - isk - otherCost;
        return new Sale(units, sold, unitPrice, profit, perLp(profit, lp), netProfit, perLp(netProfit, lp));
    }

    private static Double perLp(double profit, long lp) {
        return lp > 0 ? profit / lp : null;
    }

    static List<MarketOrderDto> sellOrders(List<MarketOrderDto> orders) {
        return orders.stream()
                .filter(order -> !order.isBuyOrder() && order.locationId() == JITA_4_4)
                .sorted(Comparator.comparingDouble(MarketOrderDto::price))
                .toList();
    }

    static List<MarketOrderDto> buyOrders(List<MarketOrderDto> orders, Map<Long, Integer> jumpsToJita) {
        return orders.stream()
                .filter(order -> order.isBuyOrder() && reachesJita44(order, jumpsToJita))
                .sorted(Comparator.comparingDouble(MarketOrderDto::price).reversed())
                .toList();
    }

    static boolean reachesJita44(MarketOrderDto buyOrder, Map<Long, Integer> jumpsToJita) {
        if (buyOrder.locationId() == JITA_4_4 || "region".equals(buyOrder.range())) {
            return true;
        }
        Integer reach = jumpRange(buyOrder.range());
        Integer jumps = buyOrder.systemId() == null ? null
                : buyOrder.systemId() == JITA ? Integer.valueOf(0) : jumpsToJita.get(buyOrder.systemId());
        return reach != null && jumps != null && jumps <= reach;
    }

    static Integer jumpRange(String range) {
        if ("solarsystem".equals(range)) {
            return 0;
        }
        try {
            return Integer.valueOf(range);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Fill fill(List<MarketOrderDto> bestFirst, long units) {
        long left = units;
        double isk = 0;
        for (MarketOrderDto order : bestFirst) {
            if (left <= 0) {
                break;
            }
            long take = Math.min(left, order.volumeRemain());
            long minimum = order.minVolume() == null ? 1 : Math.min(order.minVolume(), order.volumeRemain());
            if (take < minimum) {
                continue;
            }
            isk += take * order.price();
            left -= take;
        }
        return new Fill(units - left, isk);
    }
}
