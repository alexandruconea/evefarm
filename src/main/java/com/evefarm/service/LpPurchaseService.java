package com.evefarm.service;

import com.evefarm.db.dao.AssetDao;
import com.evefarm.db.dao.LpPurchaseDao;
import com.evefarm.db.dao.MarketOrderDao;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.db.dao.WalletJournalDao;
import com.evefarm.db.dao.WalletJournalDao.LpStorePayment;
import com.evefarm.db.dao.WalletTransactionDao;
import com.evefarm.db.dao.WalletTransactionDao.Sale;
import com.evefarm.model.EveCharacter;
import com.evefarm.model.LpOfferRow;
import com.evefarm.model.LpPurchase;
import com.evefarm.model.NpcCorporationRow;
import com.evefarm.model.PriceMode;
import com.evefarm.model.TypeInfo;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class LpPurchaseService {

    private static final Duration EVIDENCE_WINDOW = Duration.ofDays(30);
    private static final Duration RECORDED_PAYMENT_WINDOW = Duration.ofDays(7);

    public record Lot(LpPurchase purchase, long sold, double revenue, double feeRate, Double sellNow, Double buyNow,
                      Double dailyVolume) {

        public long remaining() {
            return Math.max(0, purchase.units() - purchase.usedUnits() - sold);
        }

        public double breakEven() {
            return purchase.costPerUnit() / (1 - feeRate);
        }

        public Double targetPrice() {
            Double target = purchase.targetIskPerLp();
            if (target == null || target <= 0 || purchase.units() <= 0) {
                return null;
            }
            return (target * purchase.lpSpent() + purchase.cashCost()) / purchase.units();
        }

        public Double iskPerLpNow() {
            if (sellNow == null || purchase.lpSpent() <= 0) {
                return null;
            }
            return (sellNow * purchase.units() - purchase.cashCost()) / purchase.lpSpent();
        }

        public Double profitIfSoldNow() {
            return sellNow == null ? null : remaining() * (sellNow * (1 - feeRate) - purchase.costPerUnit());
        }

        public double realizedProfit() {
            return revenue * (1 - feeRate) - sold * purchase.costPerUnit();
        }

        public boolean atTarget() {
            Double target = targetPrice();
            return remaining() > 0 && target != null && sellNow != null && sellNow >= target;
        }

        public boolean belowTarget() {
            Double target = targetPrice();
            return remaining() > 0 && target != null && sellNow != null && sellNow < target;
        }
    }

    public record Holding(int typeId, String typeName, long bought, long remaining, double averageCost,
                          double breakEven, Double targetPrice, Double sellNow, Double buyNow, Double profitIfSoldNow,
                          double realizedProfit, Double dailyVolume, List<Lot> lots) {

        public Double daysToSell() {
            if (dailyVolume == null) {
                return null;
            }
            return dailyVolume <= 0 ? Double.POSITIVE_INFINITY : remaining / dailyVolume;
        }
    }

    public record Candidate(LpOfferRow offer, int times, long listed, long sold, long held) {

        public long units() {
            return times * offer.outputUnits();
        }

        public String label() {
            return times + " x " + offer.itemName();
        }

        double likelihood() {
            return closeness(listed) + closeness(sold) + closeness(held) / 2;
        }

        private double closeness(long quantity) {
            return quantity <= 0 || units() <= 0 ? 0
                    : (double) Math.min(units(), quantity) / Math.max(units(), quantity);
        }

        @Override
        public String toString() {
            List<String> parts = new ArrayList<>();
            if (units() != times) {
                parts.add(String.format(Locale.US, "%,d units", units()));
            }
            if (listed > 0) {
                parts.add(String.format(Locale.US, "listed %,d", listed));
            }
            if (sold > 0) {
                parts.add(String.format(Locale.US, "sold %,d", sold));
            }
            if (held > 0) {
                parts.add(String.format(Locale.US, "in assets %,d", held));
            }
            return parts.isEmpty() ? label() : label() + " (" + String.join(", ", parts) + ")";
        }
    }

    public record PendingPayment(LpStorePayment payment, String characterName, String corporationName,
                                 List<Candidate> candidates) {
    }

    record Matched(long units, double revenue) {
    }

    private final LpPurchaseDao lpPurchaseDao;
    private final WalletJournalDao walletJournalDao;
    private final WalletTransactionDao walletTransactionDao;
    private final MarketOrderDao marketOrderDao;
    private final AssetDao assetDao;
    private final PriceService priceService;
    private final MarketHistoryService marketHistoryService;
    private final SellingFeesService sellingFeesService;
    private final SettingsDao settingsDao;
    private final LpOfferPricingService lpOfferPricingService;
    private final LoyaltyPointService loyaltyPointService;
    private final CharacterService characterService;
    private final TypeNameCacheService typeNameCacheService;

    public LpPurchaseService(LpPurchaseDao lpPurchaseDao, WalletJournalDao walletJournalDao,
                             WalletTransactionDao walletTransactionDao, MarketOrderDao marketOrderDao,
                             AssetDao assetDao, PriceService priceService, MarketHistoryService marketHistoryService,
                             SellingFeesService sellingFeesService, SettingsDao settingsDao,
                             LpOfferPricingService lpOfferPricingService, LoyaltyPointService loyaltyPointService,
                             CharacterService characterService, TypeNameCacheService typeNameCacheService) {
        this.lpPurchaseDao = lpPurchaseDao;
        this.walletJournalDao = walletJournalDao;
        this.walletTransactionDao = walletTransactionDao;
        this.marketOrderDao = marketOrderDao;
        this.assetDao = assetDao;
        this.priceService = priceService;
        this.marketHistoryService = marketHistoryService;
        this.sellingFeesService = sellingFeesService;
        this.settingsDao = settingsDao;
        this.lpOfferPricingService = lpOfferPricingService;
        this.loyaltyPointService = loyaltyPointService;
        this.characterService = characterService;
        this.typeNameCacheService = typeNameCacheService;
    }

    public LpPurchase purchaseFrom(LpOfferRow offer, int times, long characterId, long corporationId,
                                   String corporationName, Instant purchasedAt, Long journalEntryId) {
        double otherPerOffer = orZero(offer.requiredItemsCost()) + orZero(offer.buildMaterialsCost());
        String name = offer.outputTypeId() == offer.typeId() ? offer.itemName()
                : typeNameCacheService.findType(offer.outputTypeId()).map(TypeInfo::name)
                .orElseThrow(() -> new IllegalStateException("EVE didn't send the name of the item "
                        + offer.itemName() + " makes, so the purchase can't be recorded yet. Try again in a minute."));
        Double target = offer.iskPerLpSell() != null && offer.iskPerLpSell() > 0 ? offer.iskPerLpSell()
                : defaultTarget();
        String characterName = characterService.listCharacters().stream()
                .filter(character -> character.characterId() == characterId)
                .map(EveCharacter::characterName)
                .findFirst().orElse(null);
        return new LpPurchase(0, characterId, characterName, corporationId, corporationName, offer.offerId(),
                offer.outputTypeId(), name, times, offer.outputUnits() * times, offer.lpCost() * times,
                offer.iskCost() * times, otherPerOffer * times, target, purchasedAt, journalEntryId, 0, null);
    }

    public void record(LpPurchase purchase) {
        lpPurchaseDao.insert(purchase);
    }

    public void update(LpPurchase purchase) {
        lpPurchaseDao.update(purchase);
    }

    public void delete(long purchaseId) {
        lpPurchaseDao.delete(purchaseId);
    }

    public void ignorePayment(long journalEntryId) {
        lpPurchaseDao.ignorePayment(journalEntryId);
    }

    public List<Lot> lots() {
        List<LpPurchase> purchases = lpPurchaseDao.listAll();
        if (purchases.isEmpty()) {
            return List.of();
        }
        Set<Integer> typeIds = purchases.stream().map(LpPurchase::typeId).collect(Collectors.toSet());
        priceService.ensureFreshPrices(typeIds);
        Map<Integer, Double> sells = priceService.getUnitPrices(PriceMode.SELL_MIN);
        Map<Integer, Double> buys = priceService.getUnitPrices(PriceMode.BUY_MAX);
        Map<Integer, Double> volumes = marketHistoryService.dailyVolumes(typeIds);
        Map<Long, Matched> matched = matchSales(purchases, walletTransactionDao.findSales(typeIds));
        Map<Long, Double> feeRates = new HashMap<>();
        List<Lot> lots = new ArrayList<>();
        for (LpPurchase purchase : purchases) {
            Matched sold = matched.getOrDefault(purchase.purchaseId(), new Matched(0, 0));
            double feeRate = feeRates.computeIfAbsent(purchase.characterId(),
                    id -> sellingFeesService.characterFees(id, purchase.characterName()).sellOrderRate());
            lots.add(new Lot(purchase, sold.units(), sold.revenue(), feeRate, sells.get(purchase.typeId()),
                    buys.get(purchase.typeId()), volumes.get(purchase.typeId())));
        }
        return lots;
    }

    public static List<Holding> holdings(List<Lot> lots) {
        Map<Integer, List<Lot>> byType = new LinkedHashMap<>();
        for (Lot lot : lots) {
            byType.computeIfAbsent(lot.purchase().typeId(), type -> new ArrayList<>()).add(lot);
        }
        List<Holding> holdings = new ArrayList<>();
        for (List<Lot> group : byType.values()) {
            long remaining = group.stream().mapToLong(Lot::remaining).sum();
            Function<Lot, Double> weight = remaining > 0 ? lot -> (double) lot.remaining()
                    : lot -> (double) lot.purchase().units();
            double weights = group.stream().mapToDouble(weight::apply).sum();
            List<Lot> targeted = group.stream().filter(lot -> lot.targetPrice() != null).toList();
            double targetWeights = targeted.stream().mapToDouble(weight::apply).sum();
            Double profitIfSold = group.stream().anyMatch(lot -> lot.sellNow() == null) ? null
                    : group.stream().mapToDouble(Lot::profitIfSoldNow).sum();
            Lot first = group.getFirst();
            holdings.add(new Holding(first.purchase().typeId(), first.purchase().typeName(),
                    group.stream().mapToLong(lot -> lot.purchase().units()).sum(), remaining,
                    weighted(group, weight, weights, lot -> lot.purchase().costPerUnit()),
                    weighted(group, weight, weights, Lot::breakEven),
                    targetWeights > 0 ? weighted(targeted, weight, targetWeights, Lot::targetPrice) : null,
                    first.sellNow(), first.buyNow(), profitIfSold,
                    group.stream().mapToDouble(Lot::realizedProfit).sum(), first.dailyVolume(), List.copyOf(group)));
        }
        holdings.sort(Comparator.comparing(Holding::remaining).reversed().thenComparing(Holding::typeName));
        return holdings;
    }

    private static double weighted(List<Lot> lots, Function<Lot, Double> weight, double weights,
                                   Function<Lot, Double> value) {
        if (weights <= 0) {
            return lots.isEmpty() ? 0 : value.apply(lots.getFirst());
        }
        return lots.stream().mapToDouble(lot -> weight.apply(lot) * value.apply(lot)).sum() / weights;
    }

    static Map<Long, Matched> matchSales(List<LpPurchase> purchases, List<Sale> sales) {
        Map<Integer, List<LpPurchase>> byType = new HashMap<>();
        for (LpPurchase purchase : purchases) {
            byType.computeIfAbsent(purchase.typeId(), type -> new ArrayList<>()).add(purchase);
        }
        byType.values().forEach(list -> list.sort(Comparator.comparing(LpPurchase::purchasedAt)
                .thenComparing(LpPurchase::purchaseId)));
        Map<Long, long[]> units = new HashMap<>();
        Map<Long, double[]> revenue = new HashMap<>();
        List<Sale> ordered = new ArrayList<>(sales);
        ordered.sort(Comparator.comparing(Sale::date).thenComparing(Sale::transactionId));
        for (Sale sale : ordered) {
            long left = sale.quantity();
            for (LpPurchase lot : byType.getOrDefault(sale.typeId(), List.of())) {
                if (left <= 0 || lot.purchasedAt().isAfter(sale.date())) {
                    break;
                }
                long[] sold = units.computeIfAbsent(lot.purchaseId(), id -> new long[1]);
                long free = lot.units() - lot.usedUnits() - sold[0];
                long taken = Math.min(Math.max(0, free), left);
                if (taken > 0) {
                    sold[0] += taken;
                    revenue.computeIfAbsent(lot.purchaseId(), id -> new double[1])[0] += taken * sale.price();
                    left -= taken;
                }
            }
        }
        Map<Long, Matched> result = new HashMap<>();
        units.forEach((id, sold) -> result.put(id, new Matched(sold[0], revenue.getOrDefault(id, new double[1])[0])));
        return result;
    }

    public int pendingPaymentCount() {
        return unrecordedPayments().size();
    }

    public List<PendingPayment> pendingPayments() {
        List<LpStorePayment> payments = unrecordedPayments();
        if (payments.isEmpty()) {
            return List.of();
        }
        Map<Long, String> characters = characterService.listCharacters().stream()
                .collect(Collectors.toMap(EveCharacter::characterId, EveCharacter::characterName, (a, b) -> a));
        Map<Long, String> corporations = loyaltyPointService.listAllNpcCorporations().stream()
                .collect(Collectors.toMap(NpcCorporationRow::corporationId, NpcCorporationRow::corporationName,
                        (a, b) -> a));
        Map<Long, List<LpOfferRow>> offers = new HashMap<>();
        Map<Integer, Long> held = assetDao.usableStock(null);
        List<PendingPayment> result = new ArrayList<>();
        for (LpStorePayment payment : payments) {
            List<LpOfferRow> storeOffers = offers.computeIfAbsent(payment.corporationId(),
                    lpOfferPricingService::listPricedOffers);
            Instant until = payment.date().plus(EVIDENCE_WINDOW);
            List<Candidate> candidates = candidates(storeOffers, payment.amount(),
                    marketOrderDao.listedForSale(payment.date(), until),
                    walletTransactionDao.soldUnits(payment.date(), until), held);
            result.add(new PendingPayment(payment, characters.getOrDefault(payment.characterId(),
                    "#" + payment.characterId()), corporations.getOrDefault(payment.corporationId(),
                    "#" + payment.corporationId()), candidates));
        }
        return result;
    }

    private List<LpStorePayment> unrecordedPayments() {
        Set<Long> handled = lpPurchaseDao.handledPayments();
        List<LpStorePayment> unrecorded = new ArrayList<>(walletJournalDao.findLpStorePayments().stream()
                .filter(payment -> !handled.contains(payment.entryId()))
                .toList());
        if (unrecorded.isEmpty()) {
            return unrecorded;
        }
        for (LpPurchase purchase : lpPurchaseDao.listAll()) {
            if (purchase.journalEntryId() != null) {
                continue;
            }
            Optional<LpStorePayment> paid = paymentOf(purchase, unrecorded);
            if (paid.isPresent()) {
                lpPurchaseDao.linkPayment(purchase.purchaseId(), paid.get().entryId(), paid.get().date());
                unrecorded.remove(paid.get());
            }
        }
        return unrecorded;
    }

    static Optional<LpStorePayment> paymentOf(LpPurchase purchase, List<LpStorePayment> payments) {
        return payments.stream()
                .filter(payment -> payment.characterId() == purchase.characterId()
                        && payment.corporationId() == purchase.corporationId()
                        && Math.abs(payment.amount() - purchase.iskSpent()) < 0.5
                        && apart(purchase, payment).compareTo(RECORDED_PAYMENT_WINDOW) <= 0)
                .min(Comparator.comparing(payment -> apart(purchase, payment)));
    }

    private static Duration apart(LpPurchase purchase, LpStorePayment payment) {
        return Duration.between(purchase.purchasedAt(), payment.date()).abs();
    }

    static List<Candidate> candidates(List<LpOfferRow> offers, double amount, Map<Integer, Long> listed,
                                      Map<Integer, Long> sold, Map<Integer, Long> held) {
        List<Candidate> result = new ArrayList<>();
        for (LpOfferRow offer : offers) {
            if (offer.iskCost() <= 0) {
                continue;
            }
            double times = amount / offer.iskCost();
            long rounded = Math.round(times);
            if (rounded >= 1 && Math.abs(times - rounded) < 1e-6) {
                int type = offer.outputTypeId();
                result.add(new Candidate(offer, (int) rounded, listed.getOrDefault(type, 0L),
                        sold.getOrDefault(type, 0L), held.getOrDefault(type, 0L)));
            }
        }
        result.sort(Comparator.comparingDouble(Candidate::likelihood).reversed()
                .thenComparing(candidate -> candidate.offer().itemName()));
        return result;
    }

    private Double defaultTarget() {
        double target = settingsDao.getDouble(SettingsDao.LP_STORE_TARGET_ISK_PER_LP, 0);
        return target > 0 ? target : null;
    }

    private static double orZero(Double value) {
        return value == null ? 0 : value;
    }
}
