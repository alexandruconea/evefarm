package com.evefarm.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LpPurchasesPanelTest {

    @Test
    void theReviewButtonCountsThePaymentsWaiting() {
        assertEquals("Review Payments...", LpPurchasesPanel.reviewText(0));
        assertEquals("Review 1 Payment...", LpPurchasesPanel.reviewText(1));
        assertEquals("Review 18 Payments...", LpPurchasesPanel.reviewText(18));
    }
}
