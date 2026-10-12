package com.evefarm.service;

import com.evefarm.db.dao.WalletJournalDao;
import com.evefarm.model.MarketOrderRow;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BrokerFeeMatcherTest {

    private static final long PILOT = 2124165849L;

    private final WalletJournalDao journal = mock(WalletJournalDao.class);
    private final BrokerFeeMatcher matcher = new BrokerFeeMatcher(journal);

    private static MarketOrderRow order(long orderId, String state, double price, long volumeTotal, String issued) {
        return new MarketOrderRow(orderId, PILOT, "Malpais Legate", 34, "Tritanium", null, null, false, state,
                price, volumeTotal, volumeTotal, null, 60003760L, "Jita", issued, 90, "station", null, 0.01, null,
                null, null, null, null, null, null, null, null);
    }

    private void feesPaid(WalletJournalDao.DatedAmount... fees) {
        when(journal.findByRefType(PILOT, "brokers_fee")).thenReturn(List.of(fees));
    }

    private static WalletJournalDao.DatedAmount fee(String date, double amount) {
        return new WalletJournalDao.DatedAmount(date, amount);
    }

    @Test
    void anOrderGetsTheFeePaidWhenItWasIssued() {
        feesPaid(fee("2026-09-22T13:22:36Z", -1_175_979), fee("2026-09-22T13:27:30Z", -242_857),
                fee("2026-09-22T13:27:55Z", -126_286));

        Map<Long, Double> fees = matcher.matchFees(PILOT, List.of(
                order(7428070260L, MarketOrderRow.ACTIVE, 22_000_000, 1, "2026-09-22T13:27:30Z"),
                order(7428070440L, MarketOrderRow.ACTIVE, 11_440_000, 1, "2026-09-22T13:27:55Z")));

        assertEquals(Map.of(7428070260L, 242_857.0, 7428070440L, 126_286.0), fees);
    }

    @Test
    void anOrderWithoutAFeeAtThatMomentGetsNone() {
        feesPaid(fee("2026-09-22T13:22:36Z", -1_175_979));

        Map<Long, Double> fees = matcher.matchFees(PILOT, List.of(
                order(1, MarketOrderRow.ACTIVE, 22_000_000, 1, "2026-09-22T13:27:30Z")));

        assertFalse(fees.containsKey(1L), "a fee paid five minutes earlier belongs to another order");
    }

    @Test
    void aFeeGoesToTheOrderIssuedClosestToIt() {
        feesPaid(fee("2026-09-22T13:27:31Z", -242_857));

        Map<Long, Double> fees = matcher.matchFees(PILOT, List.of(
                order(1, MarketOrderRow.ACTIVE, 22_000_000, 1, "2026-09-22T13:27:30Z"),
                order(2, MarketOrderRow.ACTIVE, 22_000_000, 1, "2026-09-22T13:27:31Z"),
                order(3, MarketOrderRow.ACTIVE, 22_000_000, 1, "2026-09-22T13:27:34Z")));

        assertEquals(Map.of(2L, 242_857.0), fees, "each fee is paid for one order only");
    }

    @Test
    void feesPaidInTheSameSecondGoTheLargestToTheLargestOrder() {
        feesPaid(fee("2026-09-23T05:29:07Z", -169_019), fee("2026-09-23T05:29:07Z", -3_961_063));

        Map<Long, Double> fees = matcher.matchFees(PILOT, List.of(
                order(1, MarketOrderRow.ACTIVE, 15_310_000, 1, "2026-09-23T05:29:07Z"),
                order(2, MarketOrderRow.ACTIVE, 358_800_000, 1, "2026-09-23T05:29:07Z")));

        assertEquals(Map.of(1L, 169_019.0, 2L, 3_961_063.0), fees);
    }

    @Test
    void showingClosedOrdersDoesNotChangeTheFeeOfAnOrder() {
        feesPaid(fee("2026-09-22T13:27:30Z", -242_857), fee("2026-09-22T13:28:07Z", -147_922));
        MarketOrderRow active = order(1, MarketOrderRow.ACTIVE, 22_000_000, 1, "2026-09-22T13:27:30Z");
        MarketOrderRow expired = order(2, "expired", 13_400_000, 1, "2026-09-22T13:28:07Z");

        assertEquals(242_857.0, matcher.matchFees(PILOT, List.of(active)).get(1L));
        assertEquals(242_857.0, matcher.matchFees(PILOT, List.of(active, expired)).get(1L));
    }
}
