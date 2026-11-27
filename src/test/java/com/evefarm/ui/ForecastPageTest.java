package com.evefarm.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ForecastPageTest {

    @Test
    void goalsCanBeTypedShortOrInFull() {
        assertEquals(100e9, ForecastPage.IskInput.parse("100b"));
        assertEquals(2.5e12, ForecastPage.IskInput.parse("2.5T"));
        assertEquals(750e6, ForecastPage.IskInput.parse("750,000,000 ISK"));
        assertEquals(1.2e6, ForecastPage.IskInput.parse("1.2m"));
        assertNull(ForecastPage.IskInput.parse("lots"));
        assertNull(ForecastPage.IskInput.parse("-5b"));
        assertNull(ForecastPage.IskInput.parse(""));
    }

    @Test
    void aGoalIsShownTheShortWay() {
        assertEquals("100b", ForecastPage.IskInput.format(100e9));
        assertEquals(100e9, ForecastPage.IskInput.parse(ForecastPage.IskInput.format(100e9)));
    }
}
