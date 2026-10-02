package com.evefarm.service;

import java.util.LinkedHashSet;
import java.util.Set;

public record PlanCosts(double skillBooks, InjectorCalculator.InjectorPlan injectors, double injectorsPrice) {

    public static PlanCosts estimate(PriceService priceService, Set<Integer> skillBooksToBuy, long planSp,
                                     long trainedSp, long unallocatedSp) {
        Set<Integer> priced = new LinkedHashSet<>(skillBooksToBuy);
        priced.add(InjectorCalculator.LARGE_SKILL_INJECTOR);
        priced.add(InjectorCalculator.SMALL_SKILL_INJECTOR);
        priceService.ensureFreshPrices(priced);
        double books = skillBooksToBuy.stream().mapToDouble(id -> priceService.getUnitPrice(id).orElse(0)).sum();
        double largePrice = priceService.getUnitPrice(InjectorCalculator.LARGE_SKILL_INJECTOR).orElse(0);
        double smallPrice = priceService.getUnitPrice(InjectorCalculator.SMALL_SKILL_INJECTOR).orElse(0);
        InjectorCalculator.InjectorPlan injectors = InjectorCalculator.plan(planSp, trainedSp, unallocatedSp,
                largePrice, smallPrice);
        return new PlanCosts(books, injectors, injectors.cost(largePrice, smallPrice));
    }
}
