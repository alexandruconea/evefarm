package com.evefarm.service;

import com.evefarm.auth.AuthService;
import com.evefarm.db.dao.MarketOrderDao;
import com.evefarm.db.dao.WalletJournalDao;
import com.evefarm.esi.MarketsApi;
import com.evefarm.model.MarketOrderRow;
import com.evefarm.model.OrderCompetition;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarketOrderServiceTest {

    private static MarketOrderRow sellOrder(long orderId) {
        return new MarketOrderRow(orderId, 1, "Pilot", 34, "Tritanium", null, null, false, MarketOrderRow.ACTIVE,
                5.0, 10, 10, null, 60008494L, "Amarr", "2026-10-01T00:00:00Z", 90, "station", null, 0.01, null,
                null, null, null, null, null, null, null, null);
    }

    @Test
    void outbidComesFromTheLatestCheckAtTheOrdersStation() {
        MarketOrderDao orders = mock(MarketOrderDao.class);
        MarketWatchService watch = mock(MarketWatchService.class);
        when(orders.listRows(null, false)).thenReturn(List.of(sellOrder(1), sellOrder(2), sellOrder(3)));
        when(watch.latestResults()).thenReturn(Map.of(
                1L, new OrderCompetition(1, 4.5, true, Instant.now()),
                2L, new OrderCompetition(2, null, false, Instant.now())));
        MarketOrderService service = new MarketOrderService(mock(AuthService.class), mock(MarketsApi.class),
                mock(TypeNameCacheService.class), mock(LocationNameCacheService.class), orders,
                mock(PriceService.class), watch, mock(WalletJournalDao.class));

        List<MarketOrderRow> rows = service.getOrderRows(null, false);

        assertEquals(Boolean.TRUE, rows.get(0).outbid());
        assertEquals(4.5, rows.get(0).competitorPrice());
        assertEquals(Boolean.FALSE, rows.get(1).outbid());
        assertNull(rows.get(1).competitorPrice());
        assertNull(rows.get(2).outbid(), "an order that wasn't checked lately shows nothing");
    }
}
