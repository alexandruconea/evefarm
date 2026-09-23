package com.evefarm.model;

public enum PriceMode {
    SELL_MAX("Sell Maximum"),
    SELL_AVG("Sell Average"),
    SELL_MEDIAN("Sell Median"),
    SELL_PERCENTILE("Sell Percentile"),
    SELL_MIN("Sell Minimum"),
    MIDPOINT("Midpoint"),
    BUY_MAX("Buy Maximum"),
    BUY_AVG("Buy Average"),
    BUY_MEDIAN("Buy Median"),
    BUY_PERCENTILE("Buy Percentile"),
    BUY_MIN("Buy Minimum");

    private final String label;

    PriceMode(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
