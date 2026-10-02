package com.evefarm.service;

public final class InjectorCalculator {

    public static final int LARGE_SKILL_INJECTOR = 40520;
    public static final int SMALL_SKILL_INJECTOR = 45635;
    private static final int SMALL_PER_LARGE = 5;

    public record InjectorPlan(long spToInject, int large, int small, long spInjected) {

        public double cost(double largePrice, double smallPrice) {
            return large * largePrice + small * smallPrice;
        }
    }

    public static long largeInjectorSp(long totalSp) {
        if (totalSp < 5_000_000) {
            return 500_000;
        }
        if (totalSp < 50_000_000) {
            return 400_000;
        }
        if (totalSp < 80_000_000) {
            return 300_000;
        }
        return 150_000;
    }

    public static long smallInjectorSp(long totalSp) {
        return largeInjectorSp(totalSp) / SMALL_PER_LARGE;
    }

    public static InjectorPlan plan(long spNeeded, long trainedSp, long unallocatedSp, double largePrice,
                                    double smallPrice) {
        long remaining = spNeeded - unallocatedSp;
        if (remaining <= 0) {
            return new InjectorPlan(0, 0, 0, 0);
        }
        long toInject = remaining;
        long total = trainedSp + unallocatedSp;
        int large = 0;
        int small = 0;
        long injected = 0;
        while (remaining > 0) {
            long largeSp = largeInjectorSp(total);
            if (remaining >= largeSp) {
                large++;
                total += largeSp;
                injected += largeSp;
                remaining -= largeSp;
                continue;
            }
            int smallCount = 0;
            long smallSp = 0;
            long smallTotal = total;
            while (smallSp < remaining) {
                long gain = smallInjectorSp(smallTotal);
                smallCount++;
                smallSp += gain;
                smallTotal += gain;
            }
            boolean smallsAreCheaper = largePrice > 0 && smallPrice > 0
                    ? smallCount * smallPrice < largePrice
                    : smallCount < SMALL_PER_LARGE;
            if (smallsAreCheaper) {
                small += smallCount;
                injected += smallSp;
            } else {
                large++;
                injected += largeSp;
            }
            remaining = 0;
        }
        return new InjectorPlan(toInject, large, small, injected);
    }

    private InjectorCalculator() {
    }
}
