package com.evefarm.service;

import com.evefarm.service.InjectorCalculator.InjectorPlan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InjectorCalculatorTest {

    private static final double LARGE_PRICE = 900_000_000;
    private static final double SMALL_PRICE = 200_000_000;

    @Test
    void anInjectorGivesLessTheMoreSkillPointsTheCharacterHas() {
        assertEquals(500_000, InjectorCalculator.largeInjectorSp(4_999_999));
        assertEquals(400_000, InjectorCalculator.largeInjectorSp(5_000_000));
        assertEquals(300_000, InjectorCalculator.largeInjectorSp(79_999_999));
        assertEquals(150_000, InjectorCalculator.largeInjectorSp(80_000_000));
        assertEquals(30_000, InjectorCalculator.smallInjectorSp(80_000_000));
    }

    @Test
    void smallInjectorsFinishTheLastBitWhenTheyAreCheaper() {
        InjectorPlan plan = InjectorCalculator.plan(1_000_000, 4_900_000, 0, LARGE_PRICE, SMALL_PRICE);

        assertEquals(new InjectorPlan(1_000_000, 2, 2, 1_060_000), plan,
                "500k under 5M, then 400k, then two smalls of 80k for the last 100k");
        assertEquals(2 * LARGE_PRICE + 2 * SMALL_PRICE, plan.cost(LARGE_PRICE, SMALL_PRICE));
    }

    @Test
    void aLargeInjectorFinishesTheLastBitWhenSmallsWouldCostMore() {
        InjectorPlan plan = InjectorCalculator.plan(390_000, 6_000_000, 0, LARGE_PRICE, SMALL_PRICE);

        assertEquals(new InjectorPlan(390_000, 1, 0, 400_000), plan);
    }

    @Test
    void crossingEightyMillionHalvesWhatEachInjectorGives() {
        assertEquals(new InjectorPlan(600_000, 3, 0, 600_000),
                InjectorCalculator.plan(600_000, 79_762_014, 0, LARGE_PRICE, SMALL_PRICE));
    }

    @Test
    void unallocatedSkillPointsAreUsedFirst() {
        assertEquals(new InjectorPlan(0, 0, 0, 0), InjectorCalculator.plan(100_000, 10_000_000, 200_000,
                LARGE_PRICE, SMALL_PRICE));
        assertEquals(new InjectorPlan(300_000, 0, 4, 320_000), InjectorCalculator.plan(500_000, 10_000_000,
                200_000, LARGE_PRICE, SMALL_PRICE));
    }

    @Test
    void withoutPricesFewerThanFiveSmallsAreUsed() {
        assertEquals(new InjectorPlan(100_000, 0, 2, 160_000),
                InjectorCalculator.plan(100_000, 10_000_000, 0, 0, 0));
    }
}
