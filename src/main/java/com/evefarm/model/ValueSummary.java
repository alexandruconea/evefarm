package com.evefarm.model;

public record ValueSummary(
        double total,
        double walletBalance,
        double assetsValue,
        double sellOrdersValue,
        Today today,
        String bestAssetName,
        double bestAssetValue,
        String bestShipName,
        double bestShipValue,
        String bestModuleName,
        double bestModuleValue
) {

    public record Today(double isk, long lp, double targetIskPerLp) {

        public double lpValue() {
            return targetIskPerLp > 0 ? lp * targetIskPerLp : 0;
        }

        public double total() {
            return isk + lpValue();
        }

        public boolean isEmpty() {
            return isk == 0 && lp == 0;
        }
    }
}
