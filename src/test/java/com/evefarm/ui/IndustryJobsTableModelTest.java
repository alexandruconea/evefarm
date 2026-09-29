package com.evefarm.ui;

import com.evefarm.model.IndustryJobRow;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IndustryJobsTableModelTest {

    private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");

    private static IndustryJobRow job(String status, String endDate) {
        return new IndustryJobRow(1, "Pilot", 1, 1, status, "Rifter Blueprint", "Rifter", 1, 100.0, "Jita",
                "2026-09-29T10:00:00Z", endDate);
    }

    @Test
    void anActiveJobWhoseTimeIsUpIsReady() {
        assertEquals("Ready", IndustryJobsTableModel.statusText(job("active", "2026-09-29T11:00:00Z"), NOW));
        assertEquals("Active", IndustryJobsTableModel.statusText(job("active", "2026-09-29T13:00:00Z"), NOW));
    }

    @Test
    void finishedJobsKeepTheirStatus() {
        assertEquals("Delivered", IndustryJobsTableModel.statusText(job("delivered", "2026-09-29T11:00:00Z"), NOW));
        assertEquals("Cancelled", IndustryJobsTableModel.statusText(job("cancelled", "2026-09-29T13:00:00Z"), NOW));
        assertEquals("", IndustryJobsTableModel.statusText(job(null, null), NOW));
        assertEquals("Active", IndustryJobsTableModel.statusText(job("active", "not a date"), NOW));
    }
}
