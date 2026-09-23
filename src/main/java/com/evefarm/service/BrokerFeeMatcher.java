package com.evefarm.service;

import com.evefarm.db.dao.WalletJournalDao;
import com.evefarm.model.MarketOrderRow;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class BrokerFeeMatcher {

    private static final Duration MATCH_WINDOW = Duration.ofDays(2);
    private static final String BROKERS_FEE_REF_TYPE = "brokers_fee";

    private final WalletJournalDao walletJournalDao;

    BrokerFeeMatcher(WalletJournalDao walletJournalDao) {
        this.walletJournalDao = walletJournalDao;
    }

    Map<Long, Double> matchFees(long characterId, List<MarketOrderRow> ordersForCharacter) {
        List<WalletJournalDao.DatedAmount> candidates =
                new ArrayList<>(walletJournalDao.findByRefType(characterId, BROKERS_FEE_REF_TYPE));

        List<MarketOrderRow> sorted = new ArrayList<>(ordersForCharacter);
        sorted.sort((a, b) -> {
            Instant ai = parseOrEpoch(a.issued());
            Instant bi = parseOrEpoch(b.issued());
            return ai.compareTo(bi);
        });

        Map<Long, Double> result = new HashMap<>();
        for (MarketOrderRow order : sorted) {
            Instant issued = parseInstant(order.issued());
            if (issued == null) {
                continue;
            }
            double expected = Math.max(order.volumeTotal() * order.price() / 100.0 * 5.0, 100);

            WalletJournalDao.DatedAmount best = null;
            double bestDistance = Double.MAX_VALUE;
            for (WalletJournalDao.DatedAmount candidate : candidates) {
                Instant entryDate = parseInstant(candidate.date());
                if (entryDate == null || Duration.between(issued, entryDate).abs().compareTo(MATCH_WINDOW) > 0) {
                    continue;
                }
                double distance = Math.abs(Math.abs(candidate.amount()) - expected);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = candidate;
                }
            }
            if (best != null) {
                candidates.remove(best);
                result.put(order.orderId(), Math.abs(best.amount()));
            }
        }
        return result;
    }

    private Instant parseInstant(String isoDate) {
        try {
            return isoDate == null ? null : Instant.parse(isoDate);
        } catch (Exception e) {
            return null;
        }
    }

    private Instant parseOrEpoch(String isoDate) {
        Instant parsed = parseInstant(isoDate);
        return parsed == null ? Instant.EPOCH : parsed;
    }
}
