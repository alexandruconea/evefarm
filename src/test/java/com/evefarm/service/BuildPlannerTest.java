package com.evefarm.service;

import com.evefarm.model.IndustryActivity;
import com.evefarm.model.TypeQuantity;
import com.evefarm.service.BuildPlanner.Mode;
import com.evefarm.service.BuildPlanner.Plan;
import com.evefarm.service.BuildPlanner.Purchase;
import com.evefarm.service.BuildPlanner.Setup;
import com.evefarm.service.BuildPlanner.Surplus;
import com.evefarm.service.IndustryCalculator.Facility;
import com.evefarm.service.IndustryCalculator.MaterialLine;
import com.evefarm.service.IndustryCalculator.Rig;
import com.evefarm.service.IndustryCalculator.Security;
import com.evefarm.service.IndustryCalculator.Structure;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildPlannerTest {

    private static final int COMPONENT = 100;
    private static final int ALLOY = 200;
    private static final int MOON_GOO = 300;
    private static final int FUEL = 400;
    private static final int ICE = 500;
    private static final Map<Integer, IndustryActivity> PRODUCERS = Map.of(
            COMPONENT, new IndustryActivity(1, IndustryActivity.MANUFACTURING, 60,
                    List.of(new TypeQuantity(ALLOY, 10)), List.of(new TypeQuantity(COMPONENT, 1)), Map.of(),
                    List.of()),
            ALLOY, new IndustryActivity(2, IndustryActivity.REACTION, 3600,
                    List.of(new TypeQuantity(MOON_GOO, 50), new TypeQuantity(FUEL, 5)),
                    List.of(new TypeQuantity(ALLOY, 100)), Map.of(), List.of()));
    private static final Map<Integer, Double> PRICES = Map.of(COMPONENT, 1000.0, ALLOY, 5.0, MOON_GOO, 1.0,
            FUEL, 10.0);
    private static final Facility FREE = new Facility(Structure.REFINERY, Rig.NONE, Rig.NONE, Security.NULL, 0);
    private static final List<MaterialLine> THREE_COMPONENTS = List.of(new MaterialLine(COMPONENT, 3, 1000));

    private static Setup setup(Map<Integer, IndustryActivity> producers, Map<Integer, Double> prices, Mode mode) {
        return new Setup(type -> Optional.ofNullable(producers.get(type)), prices, Map.of(), FREE, FREE, 0, 0, 0, 0,
                skill -> 0, true, mode, Map.of(), Surplus.KEEP, 0.1);
    }

    private static Setup setup(boolean reactions, Mode mode, Map<Integer, Boolean> choices, Surplus surplus) {
        return new Setup(type -> Optional.ofNullable(PRODUCERS.get(type)), PRICES, Map.of(), FREE, FREE, 0, 0, 0, 0,
                skill -> 0, reactions, mode, choices, surplus, 0.1);
    }

    private static Setup setup(boolean reactions, Mode mode, Map<Integer, Boolean> choices) {
        return setup(reactions, mode, choices, Surplus.KEEP);
    }

    @Test
    void cheaperItemsAreBuiltAllTheWayDownInSteps() {
        Plan plan = BuildPlanner.plan(THREE_COMPONENTS, setup(true, Mode.CHEAPER, Map.of()), 1, 1);

        assertEquals(30, plan.materialsCost(), 1e-9);
        assertEquals(10, plan.materials().getFirst().unitPrice(), 1e-9);
        assertEquals(List.of(new Purchase(MOON_GOO, 50, 0, 1.0), new Purchase(FUEL, 5, 0, 10.0)), plan.shopping());
        assertTrue(plan.component(COMPONENT).orElseThrow().built());
        assertTrue(plan.component(ALLOY).orElseThrow().reaction());
        assertEquals(70, plan.component(ALLOY).orElseThrow().surplus());
        assertEquals(List.of(List.of(ALLOY), List.of(COMPONENT)), plan.steps().stream()
                .map(step -> step.jobs().stream().map(BuildPlanner.Component::typeId).toList()).toList());
        assertEquals(Duration.ofSeconds(3600), plan.steps().getFirst().time());
        assertEquals(Duration.ofSeconds(3780), plan.extraTime());
    }

    @Test
    void theSurplusIsKeptSoldOrWasted() {
        Plan kept = BuildPlanner.plan(THREE_COMPONENTS, setup(true, Mode.CHEAPER, Map.of(), Surplus.KEEP), 1, 1);
        Plan sold = BuildPlanner.plan(THREE_COMPONENTS, setup(true, Mode.CHEAPER, Map.of(), Surplus.SELL), 1, 1);
        Plan wasted = BuildPlanner.plan(THREE_COMPONENTS, setup(true, Mode.CHEAPER, Map.of(), Surplus.WASTE), 1, 1);

        assertEquals(70, kept.surplusValue(), 1e-9);
        assertEquals(0, kept.surplusCost(), 1e-9);
        assertEquals(70 - 70 * 5 * 0.9, sold.surplusCost(), 1e-9);
        assertEquals(100, wasted.materialsCost(), 1e-9);
    }

    @Test
    void whatTheChainNeedsOfOneItemIsMadeInOneJob() {
        Plan plan = BuildPlanner.plan(List.of(new MaterialLine(COMPONENT, 3, 1000), new MaterialLine(ALLOY, 50, 5)),
                setup(true, Mode.CHEAPER, Map.of()), 1, 1);

        assertEquals(80, plan.component(ALLOY).orElseThrow().needed());
        assertEquals(1, plan.component(ALLOY).orElseThrow().runs());
        assertEquals(20, plan.component(ALLOY).orElseThrow().surplus());
        assertEquals(List.of(new Purchase(MOON_GOO, 50, 0, 1.0), new Purchase(FUEL, 5, 0, 10.0)), plan.shopping());
        assertEquals(80, plan.keptCost(), 1e-9);
    }

    @Test
    void aChoiceToBuyStopsTheChainThere() {
        Plan plan = BuildPlanner.plan(THREE_COMPONENTS, setup(true, Mode.CHEAPER, Map.of(ALLOY, false)), 1, 1);

        assertEquals(150, plan.materialsCost(), 1e-9);
        assertEquals(List.of(new Purchase(ALLOY, 30, 0, 5.0)), plan.shopping());
        assertEquals(Duration.ofSeconds(180), plan.extraTime());
    }

    @Test
    void withoutBuildingEverythingIsBoughtButTheBuildCostIsStillShown() {
        Plan plan = BuildPlanner.plan(THREE_COMPONENTS, setup(true, Mode.BUY, Map.of()), 1, 1);

        assertEquals(3000, plan.materialsCost(), 1e-9);
        assertEquals(List.of(new Purchase(COMPONENT, 3, 0, 1000.0)), plan.shopping());
        assertEquals(1, plan.components().size());
        assertFalse(plan.component(COMPONENT).orElseThrow().built());
        assertEquals(10, plan.component(COMPONENT).orElseThrow().buildPrice(), 1e-9);
        assertTrue(plan.steps().isEmpty());
    }

    @Test
    void buildingAllMakesEveryReactionAndBuysWhatOnlyReactionsUse() {
        Map<Integer, IndustryActivity> producers = new HashMap<>(PRODUCERS);
        producers.put(FUEL, new IndustryActivity(3, IndustryActivity.MANUFACTURING, 60,
                List.of(new TypeQuantity(ICE, 1)), List.of(new TypeQuantity(FUEL, 1)), Map.of(), List.of()));
        Map<Integer, Double> prices = Map.of(COMPONENT, 1000.0, ALLOY, 0.1, MOON_GOO, 1.0, FUEL, 10.0, ICE, 1.0);

        Plan cheaper = BuildPlanner.plan(THREE_COMPONENTS, setup(producers, prices, Mode.CHEAPER), 1, 1);
        Plan all = BuildPlanner.plan(THREE_COMPONENTS, setup(producers, prices, Mode.ALL), 1, 1);

        assertFalse(cheaper.component(ALLOY).orElseThrow().built());
        assertTrue(all.component(ALLOY).orElseThrow().built());
        assertFalse(all.component(FUEL).orElseThrow().built());
        assertEquals(List.of(new Purchase(MOON_GOO, 50, 0, 1.0), new Purchase(FUEL, 5, 0, 10.0)), all.shopping());
        assertEquals(List.of(List.of(ALLOY), List.of(COMPONENT)), all.steps().stream()
                .map(step -> step.jobs().stream().map(BuildPlanner.Component::typeId).toList()).toList());
    }

    @Test
    void whatYouHaveIsTakenFromStockBeforeBuildingOrBuying() {
        Setup setup = setup(true, Mode.CHEAPER, Map.of());
        Plan plan = BuildPlanner.plan(THREE_COMPONENTS, setup, 1, 1);

        Plan stocked = BuildPlanner.withStock(THREE_COMPONENTS, setup, plan, Map.of(COMPONENT, 1L, MOON_GOO, 30L), 1, 1);

        BuildPlanner.Component component = stocked.component(COMPONENT).orElseThrow();
        assertEquals(1, component.fromStock());
        assertEquals(2, component.runs());
        assertEquals(20, stocked.component(ALLOY).orElseThrow().needed());
        assertEquals(List.of(new Purchase(MOON_GOO, 50, 30, 1.0), new Purchase(FUEL, 5, 0, 10.0)), stocked.shopping());
        assertEquals(20 * 1.0 + 5 * 10.0, stocked.purchaseCost(), 1e-9);
        assertEquals(30 * 1.0 + 1 * 1000.0, stocked.stockValue(), 1e-9);
        assertEquals(30, plan.materialsCost(), 1e-9);
    }

    @Test
    void anItemFullyInStockNeedsNoJobAndNothingBelowIt() {
        Setup setup = setup(true, Mode.CHEAPER, Map.of());
        Plan plan = BuildPlanner.plan(THREE_COMPONENTS, setup, 1, 1);

        Plan stocked = BuildPlanner.withStock(THREE_COMPONENTS, setup, plan, Map.of(ALLOY, 500L), 1, 1);

        BuildPlanner.Component alloy = stocked.component(ALLOY).orElseThrow();
        assertTrue(alloy.built());
        assertEquals(30, alloy.fromStock());
        assertEquals(0, alloy.runs());
        assertEquals(0, alloy.surplus());
        assertEquals(1.0, alloy.buildPrice(), 1e-9);
        assertEquals(List.of(), stocked.shopping());
        assertEquals(List.of(List.of(COMPONENT)), stocked.steps().stream()
                .map(step -> step.jobs().stream().map(BuildPlanner.Component::typeId).toList()).toList());
        assertEquals(0, stocked.purchaseCost(), 1e-9);
    }

    @Test
    void withoutStockThePlanStaysTheSame() {
        Setup setup = setup(true, Mode.CHEAPER, Map.of());
        Plan plan = BuildPlanner.plan(THREE_COMPONENTS, setup, 1, 1);

        assertSame(plan, BuildPlanner.withStock(THREE_COMPONENTS, setup, plan, Map.of(), 1, 1));
        assertEquals(0, plan.stockValue(), 1e-9);
        assertEquals(50 * 1.0 + 5 * 10.0, plan.purchaseCost(), 1e-9);
    }

    @Test
    void anItemWhoseBlueprintNeedsTheItemItselfIsBought() {
        Map<Integer, IndustryActivity> producers = Map.of(COMPONENT, new IndustryActivity(9,
                IndustryActivity.MANUFACTURING, 60, List.of(new TypeQuantity(COMPONENT, 1)),
                List.of(new TypeQuantity(COMPONENT, 1)), Map.of(), List.of()));

        Plan plan = BuildPlanner.plan(THREE_COMPONENTS, setup(producers, PRICES, Mode.ALL), 1, 1);

        assertTrue(plan.component(COMPONENT).isEmpty());
        assertEquals(List.of(new Purchase(COMPONENT, 3, 0, 1000.0)), plan.shopping());
        assertTrue(plan.steps().isEmpty());
    }

    @Test
    void reactionsOutsideLowAndNullSecAreBought() {
        Plan plan = BuildPlanner.plan(THREE_COMPONENTS, setup(false, Mode.CHEAPER, Map.of()), 1, 1);

        assertEquals(150, plan.materialsCost(), 1e-9);
        assertTrue(plan.component(ALLOY).isEmpty());
    }

    @Test
    void theWholeChainIsFoundForPricing() {
        assertEquals(Set.of(COMPONENT, ALLOY, MOON_GOO, FUEL), BuildPlanner.typesInvolved(List.of(COMPONENT),
                type -> Optional.ofNullable(PRODUCERS.get(type))));
    }
}
