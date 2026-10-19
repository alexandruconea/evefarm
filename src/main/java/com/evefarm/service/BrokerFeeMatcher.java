package com.evefarm.service;

import com.evefarm.db.dao.WalletJournalDao;
import com.evefarm.model.MarketOrderRow;
import com.evefarm.util.DateUtil;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class BrokerFeeMatcher {

    private static final Duration SAME_MOMENT = Duration.ofSeconds(2);
    private static final String BROKERS_FEE_REF_TYPE = "brokers_fee";
    private static final Comparator<Pairing> CLOSEST_FIRST = Comparator.comparingLong(Pairing::gapMillis)
            .thenComparing(Comparator.comparingDouble(Pairing::orderValue).reversed())
            .thenComparing(Comparator.comparingDouble(Pairing::fee).reversed());

    private final WalletJournalDao walletJournalDao;

    BrokerFeeMatcher(WalletJournalDao walletJournalDao) {
        this.walletJournalDao = walletJournalDao;
    }

    Map<Long, Double> matchFees(long characterId, List<MarketOrderRow> ordersForCharacter) {
        List<Fee> fees = new ArrayList<>();
        Map<Long, List<Fee>> feesBySecond = new HashMap<>();
        for (WalletJournalDao.DatedAmount entry : walletJournalDao.findByRefType(characterId, BROKERS_FEE_REF_TYPE)) {
            Instant paidAt = DateUtil.parseInstant(entry.date()).orElse(null);
            if (paidAt != null) {
                Fee fee = new Fee(fees.size(), paidAt, Math.abs(entry.amount()));
                fees.add(fee);
                feesBySecond.computeIfAbsent(paidAt.getEpochSecond(), second -> new ArrayList<>()).add(fee);
            }
        }

        List<Pairing> pairings = new ArrayList<>();
        for (MarketOrderRow order : ordersForCharacter) {
            Instant issued = DateUtil.parseInstant(order.issued()).orElse(null);
            if (issued == null) {
                continue;
            }
            long last = issued.plus(SAME_MOMENT).getEpochSecond();
            for (long second = issued.minus(SAME_MOMENT).getEpochSecond(); second <= last; second++) {
                for (Fee fee : feesBySecond.getOrDefault(second, List.of())) {
                    long gapMillis = Duration.between(issued, fee.paidAt()).abs().toMillis();
                    if (gapMillis <= SAME_MOMENT.toMillis()) {
                        pairings.add(new Pairing(order.orderId(), order.price() * order.volumeTotal(), fee.index(),
                                fee.amount(), gapMillis));
                    }
                }
            }
        }
        pairings.sort(CLOSEST_FIRST);

        boolean[] taken = new boolean[fees.size()];
        Map<Long, Double> result = new HashMap<>();
        for (Pairing pairing : pairings) {
            if (!taken[pairing.feeIndex()] && !result.containsKey(pairing.orderId())) {
                taken[pairing.feeIndex()] = true;
                result.put(pairing.orderId(), pairing.fee());
            }
        }
        return result;
    }

    private record Fee(int index, Instant paidAt, double amount) {
    }

    private record Pairing(long orderId, double orderValue, int feeIndex, double fee, long gapMillis) {
    }
}
