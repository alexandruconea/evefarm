package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.model.MarketOrderEntry;
import com.evefarm.model.MarketOrderRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketOrderDaoTest {

    private static final long TRADER = 90_000_001L;

    private MarketOrderDao orders;

    @BeforeEach
    void setUp() {
        Database database = new Database(":memory:");
        MigrationRunner.run(database);
        new CharacterDao(database).upsert(TRADER, "Trader", null, List.of(), "owner");
        orders = new MarketOrderDao(database);
    }

    private static MarketOrderEntry order(long orderId, String state, long remaining) {
        return new MarketOrderEntry(orderId, 34, false, 5.0, remaining, 1_000, null, 60003760L,
                "2026-09-" + (10 + orderId) + "T10:00:00Z", 90, state, "region", null);
    }

    private Map<Long, String> states(boolean includeClosed) {
        return orders.listRows(null, includeClosed).stream()
                .collect(Collectors.toMap(MarketOrderRow::orderId, MarketOrderRow::state));
    }

    @Test
    void closedOrdersStayAfterEsiForgetsThem() {
        orders.saveForCharacter(TRADER, List.of(order(1, "active", 400), order(2, "expired", 0)));

        orders.saveForCharacter(TRADER, List.of(order(1, "active", 300)));

        assertEquals(Map.of(1L, "active", 2L, "expired"), states(true));
        assertEquals(Map.of(1L, "active"), states(false), "closed orders only show when asked for");
    }

    @Test
    void anOrderThatLeftTheActiveListIsClosedUntilItsHistoryArrives() {
        orders.saveForCharacter(TRADER, List.of(order(1, "active", 400)));

        orders.saveForCharacter(TRADER, List.of());
        assertEquals(Map.of(1L, MarketOrderRow.CLOSED), states(true));

        orders.saveForCharacter(TRADER, List.of(order(1, "cancelled", 120)));
        assertEquals(Map.of(1L, "cancelled"), states(true));
        assertEquals(120, orders.listRows(null, true).getFirst().volumeRemain());
    }

    @Test
    void existingOrdersFromBeforeTheHistoryWereActive() {
        orders.saveForCharacter(TRADER, List.of(order(1, "active", 400)));

        assertEquals(List.of(1L), orders.listRows(null, false).stream().map(MarketOrderRow::orderId).toList());
    }
}
