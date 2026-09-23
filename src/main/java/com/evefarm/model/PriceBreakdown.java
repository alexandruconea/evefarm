package com.evefarm.model;

public record PriceBreakdown(
        Double sellMax,
        Double sellAvg,
        Double sellMedian,
        Double sellPercentile,
        Double sellMin,
        Double buyMax,
        Double buyAvg,
        Double buyMedian,
        Double buyPercentile,
        Double buyMin,
        Double sellVolume,
        Double buyVolume
) {
}
