package com.evefarm.service;

import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.db.dao.AssetDao;
import com.evefarm.db.dao.CharacterDao;
import com.evefarm.db.dao.LpPurchaseDao;
import com.evefarm.db.dao.MarketOrderDao;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.db.dao.WalletJournalDao;
import com.evefarm.db.dao.WalletJournalDao.LpStorePayment;
import com.evefarm.db.dao.WalletTransactionDao;
import com.evefarm.db.dao.WalletTransactionDao.Sale;
import com.evefarm.model.CharacterSkills;
import com.evefarm.model.EveCharacter;
import com.evefarm.model.JournalEntry;
import com.evefarm.model.LpOfferRow;
import com.evefarm.model.LpPurchase;
import com.evefarm.model.MarketOrderEntry;
import com.evefarm.model.NpcCorporationRow;
import com.evefarm.model.OwnedSkill;
import com.evefarm.model.PriceMode;
import com.evefarm.model.StandingRow;
import com.evefarm.model.TransactionEntry;
import com.evefarm.model.TypeInfo;
import com.evefarm.service.LpPurchaseService.Candidate;
import com.evefarm.service.LpPurchaseService.Holding;
import com.evefarm.service.LpPurchaseService.Lot;
import com.evefarm.service.LpPurchaseService.Matched;
import com.evefarm.service.LpPurchaseService.PendingPayment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LpPurchaseServiceTest {

    private static final long PILOT = 2124165849L;
    private static final long CALDARI_NAVY = 1000035L;
    private static final int WEBIFIER = 17559;
    private static final int BALLISTIC = 15681;
    private static final int GOLEM_BLUEPRINT = 28711;
    private static final int GOLEM = 28710;
    private static final int ACCOUNTING = 16622;
    private static final int BROKER_RELATIONS = 3446;

    private final PriceService prices = mock(PriceService.class);
    private final MarketHistoryService history = mock(MarketHistoryService.class);
    private final SkillService skills = mock(SkillService.class);
    private final StandingService standings = mock(StandingService.class);
    private final LpOfferPricingService offers = mock(LpOfferPricingService.class);
    private final LoyaltyPointService loyalty = mock(LoyaltyPointService.class);
    private final CharacterService characters = mock(CharacterService.class);
    private final TypeNameCacheService types = mock(TypeNameCacheService.class);
    private SettingsDao settings;
    private WalletJournalDao journal;
    private WalletTransactionDao transactions;
    private MarketOrderDao orders;
    private LpPurchaseDao purchases;
    private LpPurchaseService service;

    @BeforeEach
    void setUp() {
        Database database = new Database(":memory:");
        MigrationRunner.run(database);
        new CharacterDao(database).upsert(PILOT, "Malpais Legate", List.of(), null);
        settings = new SettingsDao(database);
        journal = new WalletJournalDao(database);
        transactions = new WalletTransactionDao(database);
        orders = new MarketOrderDao(database);
        purchases = new LpPurchaseDao(database);
        when(characters.listCharacters()).thenReturn(List.of(new EveCharacter(PILOT, "Malpais Legate", List.of(),
                Instant.parse("2026-01-01T00:00:00Z"))));
        service = new LpPurchaseService(purchases, journal, transactions, orders,
                new AssetDao(database), prices, history, new SellingFeesService(characters, skills, standings),
                settings, offers, loyalty, characters, types);
    }

    private static LpPurchase purchase(long id, int typeId, long units, double cashCost, String boughtOn) {
        return new LpPurchase(id, PILOT, "Malpais Legate", CALDARI_NAVY, "Caldari Navy", 1, typeId, "Item " + typeId,
                (int) units, units, units * 1_500, cashCost, 0, 1_500.0, Instant.parse(boughtOn), null, 0, null);
    }

    private static LpPurchase used(LpPurchase purchase, long usedUnits) {
        return new LpPurchase(purchase.purchaseId(), purchase.characterId(), purchase.characterName(),
                purchase.corporationId(), purchase.corporationName(), purchase.offerId(), purchase.typeId(),
                purchase.typeName(), purchase.offers(), purchase.units(), purchase.lpSpent(), purchase.iskSpent(),
                purchase.otherCost(), purchase.targetIskPerLp(), purchase.purchasedAt(), purchase.journalEntryId(),
                usedUnits, purchase.notes());
    }

    private static Sale sale(long id, String date, int typeId, long quantity, double price) {
        return new Sale(id, Instant.parse(date), typeId, quantity, price);
    }

    private static LpOfferRow offer(long offerId, int typeId, String name, long lpCost, double iskCost,
                                    Double requiredItemsCost, Double iskPerLp, int outputTypeId, long outputUnits) {
        return new LpOfferRow(offerId, typeId, name, "Module", outputUnits, lpCost, iskCost, 0, "", List.of(),
                requiredItemsCost, "", List.of(), 0.0, null, null, iskPerLp, null, null, null, outputTypeId,
                outputUnits, null, null, null);
    }

    private static JournalEntry payment(long entryId, String date, String refType, double amount) {
        return new JournalEntry(entryId, date, refType, amount, 0, "", "", (int) PILOT, (int) CALDARI_NAVY, 0, null);
    }

    private static LpPurchase recordedInTheLpStore(long id, String boughtOn, double iskSpent) {
        return new LpPurchase(id, PILOT, "Malpais Legate", CALDARI_NAVY, "Caldari Navy", 2, BALLISTIC,
                "Ballistic Control", 1, 1, 4_500, iskSpent, 0, null, Instant.parse(boughtOn), null, 0, null);
    }

    @Test
    void salesGoToTheOldestPurchaseMadeBeforeThem() {
        List<LpPurchase> purchases = List.of(
                purchase(2, WEBIFIER, 5, 5_000_000, "2026-10-05T12:00:00Z"),
                purchase(1, WEBIFIER, 10, 10_000_000, "2026-10-01T12:00:00Z"),
                purchase(3, BALLISTIC, 4, 4_000_000, "2026-10-01T12:00:00Z"));
        List<Sale> sales = List.of(
                sale(14, "2026-10-07T12:00:00Z", WEBIFIER, 5, 400),
                sale(11, "2026-09-30T12:00:00Z", WEBIFIER, 3, 100),
                sale(12, "2026-10-03T12:00:00Z", WEBIFIER, 8, 200),
                sale(13, "2026-10-06T12:00:00Z", WEBIFIER, 6, 300));

        Map<Long, Matched> matched = LpPurchaseService.matchSales(purchases, sales);

        assertEquals(Map.of(1L, new Matched(10, 8 * 200 + 2 * 300), 2L, new Matched(5, 4 * 300 + 400)), matched,
                "the sale before every purchase and what is sold beyond the units bought stay unmatched");
    }

    @Test
    void unitsMarkedAsUsedAreNotSold() {
        List<LpPurchase> purchases = List.of(
                used(purchase(1, WEBIFIER, 10, 10_000_000, "2026-10-01T12:00:00Z"), 4),
                purchase(2, WEBIFIER, 5, 5_000_000, "2026-10-02T12:00:00Z"));

        Map<Long, Matched> matched = LpPurchaseService.matchSales(purchases,
                List.of(sale(1, "2026-10-03T12:00:00Z", WEBIFIER, 8, 100)));

        assertEquals(Map.of(1L, new Matched(6, 600), 2L, new Matched(2, 200)), matched);
    }

    @Test
    void aPaymentMatchesTheOffersWhoseIskCostGoesIntoItAWholeNumberOfTimes() {
        List<LpOfferRow> storeOffers = List.of(
                offer(1, WEBIFIER, "Stasis Webifier", 1_500, 1_000_000, 0.0, null, WEBIFIER, 1),
                offer(2, BALLISTIC, "Ballistic Control", 4_500, 3_000_000, 0.0, null, BALLISTIC, 1),
                offer(3, 1, "Free Item", 100, 0, 0.0, null, 1, 1),
                offer(4, 2, "Odd Item", 700, 700_000, 0.0, null, 2, 1),
                offer(5, GOLEM_BLUEPRINT, "Golem Blueprint", 300_000, 1_500_000, 0.0, null, GOLEM, 1));

        List<Candidate> candidates = LpPurchaseService.candidates(storeOffers, 3_000_000, Map.of(), Map.of(),
                Map.of());

        assertEquals(List.of("1 x Ballistic Control", "2 x Golem Blueprint", "3 x Stasis Webifier"),
                candidates.stream().map(Candidate::toString).toList(), "with nothing to go by, by name");
    }

    @Test
    void theOfferThatFitsWhatYouListedSoldAndHaveComesFirst() {
        List<LpOfferRow> storeOffers = List.of(
                offer(1, WEBIFIER, "Stasis Webifier", 1_500, 1_000_000, 0.0, null, WEBIFIER, 1),
                offer(2, BALLISTIC, "Ballistic Control", 4_500, 3_000_000, 0.0, null, BALLISTIC, 1),
                offer(5, GOLEM_BLUEPRINT, "Golem Blueprint", 300_000, 1_500_000, 0.0, null, GOLEM, 5));

        List<Candidate> candidates = LpPurchaseService.candidates(storeOffers, 3_000_000,
                Map.of(WEBIFIER, 40L, GOLEM, 10L), Map.of(WEBIFIER, 40L, GOLEM, 9L, BALLISTIC, 1L),
                Map.of(BALLISTIC, 6L));

        assertEquals(List.of("2 x Golem Blueprint (10 units, listed 10, sold 9)",
                "1 x Ballistic Control (sold 1, in assets 6)", "3 x Stasis Webifier (listed 40, sold 40)"),
                candidates.stream().map(Candidate::toString).toList());
        assertEquals("2 x Golem Blueprint", candidates.getFirst().label());
    }

    @Test
    void aLotKnowsItsBreakEvenTargetAndProfit() {
        LpPurchase bought = new LpPurchase(1, PILOT, "Malpais Legate", CALDARI_NAVY, "Caldari Navy", 1, WEBIFIER,
                "Caldari Navy Stasis Webifier", 30, 30, 45_000, 30_000_000, 0, 1_500.0,
                Instant.parse("2026-10-01T12:00:00Z"), null, 0, null);
        Lot lot = new Lot(bought, 10, 30_000_000, 0.05, 3_250_000.0, 3_000_000.0, 4.0);

        assertEquals(20, lot.remaining());
        assertEquals(1_000_000 / 0.95, lot.breakEven(), 1e-6);
        assertEquals(3_250_000, lot.targetPrice(), 1e-6, "the price at which the LP Store tab shows 1,500 ISK/LP");
        assertEquals(1_500, lot.iskPerLpNow(), 1e-9);
        assertEquals(20 * (3_250_000 * 0.95 - 1_000_000), lot.profitIfSoldNow(), 1e-6);
        assertEquals(30_000_000 * 0.95 - 10 * 1_000_000, lot.realizedProfit(), 1e-6);
        assertTrue(lot.atTarget());
        assertFalse(lot.belowTarget());

        Lot cheaper = new Lot(bought, 10, 30_000_000, 0.05, 3_000_000.0, null, null);
        assertFalse(cheaper.atTarget());
        assertTrue(cheaper.belowTarget());

        Lot soldOut = new Lot(bought, 30, 90_000_000, 0.05, 3_250_000.0, null, null);
        assertFalse(soldOut.atTarget(), "nothing left to sell");
        assertFalse(soldOut.belowTarget());

        LpPurchase noTarget = new LpPurchase(1, PILOT, "Malpais Legate", CALDARI_NAVY, "Caldari Navy", 1, WEBIFIER,
                "Caldari Navy Stasis Webifier", 30, 30, 45_000, 30_000_000, 0, null,
                Instant.parse("2026-10-01T12:00:00Z"), null, 0, null);
        Lot untargeted = new Lot(noTarget, 0, 0, 0.05, 3_250_000.0, null, null);
        assertNull(untargeted.targetPrice());
        assertFalse(untargeted.atTarget());
        assertFalse(untargeted.belowTarget());
    }

    @Test
    void holdingsAverageTheirPurchasesByWhatIsLeft() {
        Lot first = new Lot(purchase(1, WEBIFIER, 30, 30_000_000, "2026-10-01T12:00:00Z"), 10, 30_000_000, 0.05,
                3_250_000.0, null, 4.0);
        Lot second = new Lot(purchase(2, WEBIFIER, 10, 20_000_000, "2026-10-04T12:00:00Z"), 0, 0, 0.05,
                3_250_000.0, null, 4.0);
        Lot other = new Lot(purchase(3, BALLISTIC, 50, 5_000_000, "2026-10-02T12:00:00Z"), 0, 0, 0.05,
                200_000.0, null, null);

        List<Holding> holdings = LpPurchaseService.holdings(List.of(first, second, other));

        assertEquals(List.of(BALLISTIC, WEBIFIER), holdings.stream().map(Holding::typeId).toList(),
                "most units left first");
        Holding webifiers = holdings.get(1);
        assertEquals(40, webifiers.bought());
        assertEquals(30, webifiers.remaining());
        assertEquals((20 * 1_000_000.0 + 10 * 2_000_000.0) / 30, webifiers.averageCost(), 1e-6);
        assertEquals((20 * first.targetPrice() + 10 * second.targetPrice()) / 30, webifiers.targetPrice(), 1e-6);
        assertEquals(first.profitIfSoldNow() + second.profitIfSoldNow(), webifiers.profitIfSoldNow(), 1e-6);
        assertEquals(first.realizedProfit(), webifiers.realizedProfit(), 1e-6);
        assertEquals(30 / 4.0, webifiers.daysToSell(), 1e-9);

        Lot soldOut = new Lot(purchase(4, GOLEM, 2, 1_000_000, "2026-10-01T12:00:00Z"), 2, 2_000_000, 0.05,
                null, null, null);
        Lot soldOutToo = new Lot(purchase(5, GOLEM, 6, 9_000_000, "2026-10-02T12:00:00Z"), 6, 12_000_000, 0.05,
                null, null, null);
        Holding golems = LpPurchaseService.holdings(List.of(soldOut, soldOutToo)).getFirst();
        assertEquals((2 * 500_000.0 + 6 * 1_500_000.0) / 8, golems.averageCost(), 1e-6,
                "with nothing left, every unit bought counts");
        assertNull(golems.profitIfSoldNow(), "no price, no estimate");
    }

    @Test
    void lotsUseTheMarketPricesAndTheSellersTaxAndFee() {
        service.record(purchase(0, WEBIFIER, 30, 30_000_000, "2026-10-01T12:00:00Z"));
        transactions.saveForCharacter(PILOT, List.of(
                new TransactionEntry(1, "2026-10-02T12:00:00Z", WEBIFIER, 12, 3_300_000, 1, 60003760L, false, true, 1),
                new TransactionEntry(2, "2026-10-03T12:00:00Z", WEBIFIER, 5, 3_000_000, 1, 60003760L, true, true, 2)));
        when(prices.getUnitPrices(PriceMode.SELL_MIN)).thenReturn(Map.of(WEBIFIER, 3_250_000.0));
        when(prices.getUnitPrices(PriceMode.BUY_MAX)).thenReturn(Map.of(WEBIFIER, 3_100_000.0));
        when(history.dailyVolumes(any())).thenReturn(Map.of(WEBIFIER, 4.0));
        when(skills.skills(PILOT)).thenReturn(Optional.of(new CharacterSkills(PILOT,
                List.of(new OwnedSkill(ACCOUNTING, 256_000, 5, 5), new OwnedSkill(BROKER_RELATIONS, 45_255, 4, 4)),
                null, List.of(), 0, 0, null, null, null, null)));
        when(standings.getRows()).thenReturn(List.of(new StandingRow(PILOT, "Malpais Legate",
                SellingFeesService.JITA_STATION_CORPORATION, "npc_corp", "Caldari Navy", 5.0, null, null, null, null,
                null)));

        Lot lot = service.lots().getFirst();

        assertEquals("Malpais Legate", lot.purchase().characterName());
        assertEquals(12, lot.sold(), "the purchase on 2026-10-03 is not a sale");
        assertEquals(12 * 3_300_000, lot.revenue(), 1e-6);
        assertEquals(0.075 * (1 - 0.11 * 5) + 0.03 - 0.003 * 4 - 0.0002 * 5, lot.feeRate(), 1e-12);
        assertEquals(3_250_000, lot.sellNow(), 1e-9);
        assertEquals(3_100_000, lot.buyNow(), 1e-9);
        assertEquals(4.0, lot.dailyVolume(), 1e-9);

        when(skills.skills(PILOT)).thenReturn(Optional.empty());
        assertEquals(0.075 * (1 - 0.11 * 4) + 0.03 - 0.003 * 4 - 0.0002 * 5, service.lots().getFirst().feeRate(),
                1e-12, "unknown skills count as level IV");
    }

    @Test
    void paymentsAlreadyRecordedOrIgnoredAreNotPending() {
        orders.saveForCharacter(PILOT, List.of(
                new MarketOrderEntry(1, BALLISTIC, false, 3_300_000, 0, 1, null, 60003760L, "2026-10-01T13:00:00Z",
                        90, "expired", "region", null),
                new MarketOrderEntry(2, WEBIFIER, false, 1_100_000, 0, 3, null, 60003760L, "2026-09-30T13:00:00Z",
                        90, "expired", "region", null)));
        journal.saveForCharacter(PILOT, List.of(
                payment(1, "2026-10-01T12:00:00Z", "lp_store", -3_000_000),
                payment(2, "2026-10-02T12:00:00Z", "lp_store", -1_000_000),
                payment(3, "2026-10-03T12:00:00Z", "lp_store", 1_000_000),
                payment(4, "2026-10-04T12:00:00Z", "bounty_prizes", -1_000_000),
                payment(5, "2026-10-05T12:00:00Z", "lp_store", -2_000_000)));
        service.record(new LpPurchase(0, PILOT, null, CALDARI_NAVY, "Caldari Navy", 1, WEBIFIER, "Webifier", 1, 1,
                1_500, 1_000_000, 0, null, Instant.parse("2026-10-02T12:00:00Z"), 2L, 0, null));
        service.ignorePayment(5);
        when(loyalty.listAllNpcCorporations()).thenReturn(List.of(new NpcCorporationRow(CALDARI_NAVY,
                "Caldari Navy")));
        when(offers.listPricedOffers(CALDARI_NAVY)).thenReturn(List.of(
                offer(1, WEBIFIER, "Stasis Webifier", 1_500, 1_000_000, 0.0, null, WEBIFIER, 1),
                offer(2, BALLISTIC, "Ballistic Control", 4_500, 3_000_000, 0.0, null, BALLISTIC, 1)));

        assertEquals(1, service.pendingPaymentCount());
        PendingPayment pending = service.pendingPayments().getFirst();
        assertEquals(1, pending.payment().entryId());
        assertEquals(3_000_000, pending.payment().amount(), 1e-9);
        assertEquals("Malpais Legate", pending.characterName());
        assertEquals("Caldari Navy", pending.corporationName());
        assertEquals(List.of("1 x Ballistic Control (listed 1)", "3 x Stasis Webifier"),
                pending.candidates().stream().map(Candidate::toString).toList(),
                "the webifiers were listed before the payment");
    }

    @Test
    void aPurchaseCountsTheOfferAsManyTimesAsItWasBought() {
        Instant boughtOn = Instant.parse("2026-10-09T14:30:00Z");
        LpPurchase webifiers = service.purchaseFrom(offer(7, WEBIFIER, "Caldari Navy Stasis Webifier", 1_500,
                1_000_000, 50_000.0, 1_480.0, WEBIFIER, 1), 30, PILOT, CALDARI_NAVY, "Caldari Navy", boughtOn, 99L);

        assertEquals(new LpPurchase(0, PILOT, "Malpais Legate", CALDARI_NAVY, "Caldari Navy", 7, WEBIFIER,
                "Caldari Navy Stasis Webifier", 30, 30, 45_000, 30_000_000, 1_500_000, 1_480.0, boughtOn, 99L, 0,
                null), webifiers);

        when(types.findType(GOLEM)).thenReturn(Optional.of(new TypeInfo(GOLEM, "Golem", "Marauder", "Ship", 0)));
        settings.set(SettingsDao.LP_STORE_TARGET_ISK_PER_LP, "1200");
        LpPurchase golems = service.purchaseFrom(offer(8, GOLEM_BLUEPRINT, "Golem Blueprint", 300_000, 50_000_000,
                null, null, GOLEM, 1), 2, PILOT, CALDARI_NAVY, "Caldari Navy", boughtOn, null);

        assertEquals(GOLEM, golems.typeId());
        assertEquals("Golem", golems.typeName(), "a blueprint offer is tracked as the item it builds");
        assertEquals(1_200.0, golems.targetIskPerLp(), "without an ISK/LP the LP Store target is used");
        assertEquals(0, golems.otherCost(), 1e-9);
    }

    @Test
    void aPurchaseIsNotRecordedUnderAPlaceholderName() {
        when(types.findType(GOLEM)).thenReturn(Optional.empty());

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.purchaseFrom(
                offer(8, GOLEM_BLUEPRINT, "Golem Blueprint", 300_000, 50_000_000, null, null, GOLEM, 1), 2, PILOT,
                CALDARI_NAVY, "Caldari Navy", Instant.parse("2026-10-09T14:30:00Z"), null));

        assertTrue(error.getMessage().contains("Golem Blueprint"), error.getMessage());
    }

    @Test
    void anOfferThatLosesIskAtTheSellPriceIsNoTarget() {
        Instant boughtOn = Instant.parse("2026-09-22T14:03:53Z");
        LpOfferRow losing = offer(9, WEBIFIER, "Caldari Navy Stasis Webifier", 3_750, 12_000_000, 0.0, -53.3,
                WEBIFIER, 1);

        assertNull(service.purchaseFrom(losing, 20, PILOT, CALDARI_NAVY, "Caldari Navy", boughtOn, null)
                .targetIskPerLp(), "no target to start from");

        settings.set(SettingsDao.LP_STORE_TARGET_ISK_PER_LP, "800");
        assertEquals(800.0, service.purchaseFrom(losing, 20, PILOT, CALDARI_NAVY, "Caldari Navy", boughtOn, null)
                .targetIskPerLp(), "the LP Store target instead of a loss");
    }

    @Test
    void aPurchaseRecordedInTheLpStoreTabIsMatchedToItsPayment() {
        journal.saveForCharacter(PILOT, List.of(
                payment(1, "2026-09-29T12:00:00Z", "lp_store", -3_000_000),
                payment(2, "2026-10-01T12:00:00Z", "lp_store", -3_000_000),
                payment(3, "2026-10-01T12:05:00Z", "lp_store", -1_000_000)));
        service.record(recordedInTheLpStore(0, "2026-10-01T12:20:00Z", 3_000_000));

        assertEquals(2, service.pendingPaymentCount(), "the same amount two days earlier, and another amount");
        assertEquals(2, service.pendingPaymentCount());
        LpPurchase linked = purchases.listAll().getFirst();
        assertEquals(2L, linked.journalEntryId());
        assertEquals(Instant.parse("2026-10-01T12:00:00Z"), linked.purchasedAt(), "bought when it was paid");
    }

    @Test
    void aRecordedPurchaseGoesToTheClosestPaymentOfTheSameAmount() {
        LpPurchase recorded = recordedInTheLpStore(1, "2026-10-05T12:30:00Z", 3_000_000);
        LpStorePayment earlier = paid(7, "2026-10-01T12:00:00Z", 3_000_000);
        LpStorePayment closest = paid(8, "2026-10-05T12:00:00Z", 3_000_000);
        LpStorePayment otherAmount = paid(9, "2026-10-05T12:20:00Z", 1_000_000);
        LpStorePayment weekLater = paid(10, "2026-10-13T12:00:00Z", 3_000_000);

        assertEquals(Optional.of(closest),
                LpPurchaseService.paymentOf(recorded, List.of(earlier, closest, otherAmount, weekLater)));
        assertEquals(Optional.empty(), LpPurchaseService.paymentOf(recorded, List.of(otherAmount, weekLater)));
    }

    private static LpStorePayment paid(long entryId, String date, double amount) {
        return new LpStorePayment(PILOT, entryId, Instant.parse(date), amount, CALDARI_NAVY);
    }
}
