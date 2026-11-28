package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.model.ContractEntry;
import com.evefarm.model.ContractItemEntry;
import com.evefarm.model.ContractRow;
import com.evefarm.model.Corporation;
import com.evefarm.model.MarketOrderEntry;
import com.evefarm.model.MarketOrderRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorporationMarketDaoTest {

    private static final long CORPORATION = 98_000_001L;
    private static final Instant NOW = Instant.parse("2026-11-28T10:00:00Z");

    private CorporationMarketDao dao;

    @BeforeEach
    void setUp() {
        Database database = new Database(":memory:");
        MigrationRunner.run(database);
        CorporationDao corporations = new CorporationDao(database);
        corporations.saveCorporation(new Corporation(CORPORATION, "Deep Core Mining", "DCM"));
        corporations.saveDivisions(CORPORATION, CorporationDao.WALLET, Map.of(3, "Trade"));
        dao = new CorporationMarketDao(database, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static CorporationMarketDao.Order order(long orderId, boolean buy, double price, long remaining,
                                                    String state, Integer division) {
        return new CorporationMarketDao.Order(new MarketOrderEntry(orderId, 34, buy, price, remaining, remaining * 2,
                buy ? price * remaining : null, 60003760L, "2026-11-27T10:00:00Z", 90, state, "station", null),
                division);
    }

    @Test
    void ordersSayWhichWalletPaysAndOnlyOpenSellOrdersAddUp() {
        dao.saveOrders(CORPORATION, List.of(order(1, false, 10, 5, MarketOrderRow.ACTIVE, 3),
                order(2, true, 4, 100, MarketOrderRow.ACTIVE, 1), order(3, false, 99, 9, "expired", 1)));

        List<MarketOrderRow> open = dao.listOrderRows(false);

        assertEquals(List.of(1L, 2L), open.stream().map(MarketOrderRow::orderId).sorted().toList());
        assertEquals("Deep Core Mining [DCM] · Trade", open.stream().filter(row -> row.orderId() == 1)
                .findFirst().orElseThrow().characterName());
        assertTrue(open.stream().allMatch(MarketOrderRow::corporation));
        assertEquals(3, dao.listOrderRows(true).size());
        assertEquals(50, dao.sellOrdersValue(CORPORATION), 1e-9, "the buy order and the expired one don't count");
    }

    @Test
    void anOrderNoLongerOpenIsClosed() {
        dao.saveOrders(CORPORATION, List.of(order(1, false, 10, 5, MarketOrderRow.ACTIVE, 1)));

        dao.saveOrders(CORPORATION, List.of());

        assertEquals(List.of(), dao.listOrderRows(false));
        assertEquals(MarketOrderRow.CLOSED, dao.listOrderRows(true).getFirst().state());
        assertEquals(0, dao.sellOrdersValue(CORPORATION), 1e-9);
    }

    @Test
    void contractsBelongToTheCorporationAndKeepTheirItems() {
        dao.saveContracts(CORPORATION, List.of(new ContractEntry(7, "item_exchange", "outstanding", "Ore",
                null, 1_000.0, null, 10.0, "2026-11-27T10:00:00Z", "2026-12-11T10:00:00Z", null, true, 90, null, null,
                60003760L, 60003760L)));

        ContractRow row = dao.listContractRows().getFirst();
        assertEquals("Deep Core Mining [DCM]", row.characterName());
        assertEquals(CORPORATION, row.characterId());
        assertTrue(row.corporation());
        assertTrue(dao.findContractItems(CORPORATION, 7).isEmpty(), "not fetched yet");

        List<ContractItemEntry> items = List.of(new ContractItemEntry(1, 34, 1_000, null, true));
        dao.saveContractItems(CORPORATION, 7, items);

        assertEquals(items, dao.findContractItems(CORPORATION, 7).orElseThrow());
    }
}
