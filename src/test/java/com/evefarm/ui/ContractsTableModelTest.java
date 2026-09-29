package com.evefarm.ui;

import com.evefarm.model.ContractRow;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContractsTableModelTest {

    private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");

    private static ContractRow contract(String status, String expires) {
        return new ContractRow(1, "Trader", 2, "item_exchange", status, "", null, 100.0, null, null,
                "2026-09-01T10:00:00Z", expires, null, "Trader", null, null, "Jita", "Jita");
    }

    @Test
    void anOutstandingContractPastItsExpiryShowsAsExpired() {
        assertEquals("Expired", ContractsTableModel.statusText(contract("outstanding", "2026-09-20T10:00:00Z"), NOW));
        assertEquals("Outstanding",
                ContractsTableModel.statusText(contract("outstanding", "2026-10-20T10:00:00Z"), NOW));
        assertEquals("Finished", ContractsTableModel.statusText(contract("finished", "2026-09-20T10:00:00Z"), NOW));
    }

    @Test
    void monthsAreNamedForTheAssetsArchive() {
        assertEquals("September 2026", AssetsPanel.monthName("2026-09"));
        assertEquals("not-a-month", AssetsPanel.monthName("not-a-month"));
    }
}
