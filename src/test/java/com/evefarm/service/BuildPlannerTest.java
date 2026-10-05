package com.evefarm.service;

import com.evefarm.model.IndustryActivity;
import com.evefarm.model.TypeQuantity;
import com.evefarm.service.BuildPlanner.Plan;
import com.evefarm.service.BuildPlanner.Setup;
import com.evefarm.service.IndustryCalculator.Facility;
import com.evefarm.service.IndustryCalculator.MaterialLine;
import com.evefarm.service.IndustryCalculator.Rig;
import com.evefarm.service.IndustryCalculator.Security;
import com.evefarm.service.IndustryCalculator.Structure;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildPlannerTest {

    private static final int COMPONENT = 100;
    private static final int ALLOY = 200;
    private static final int MOON_GOO = 300;
    private static final int FUEL = 400;
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

    private static Setup setup(boolean reactions, boolean buildWhenCheaper, Map<Integer, Boolean> choices) {
        return new Setup(type -> Optional.ofNullable(PRODUCERS.get(type)), PRICES, Map.of(), FREE, FREE, 0, 0, 0, 0,
                skill -> 0, reactions, buildWhenCheaper, choices);
    }

    @Test
    void cheaperItemsAreBuiltAllTheWayDown() {
        Plan plan = BuildPlanner.plan(THREE_COMPONENTS, setup(true, true, Map.of()), 1, 1);

        assertEquals(30, plan.materialsCost(), 1e-9);
        assertEquals(10, plan.materials().getFirst().unitPrice(), 1e-9);
        assertEquals(List.of(new MaterialLine(MOON_GOO, 50, 1.0), new MaterialLine(FUEL, 5, 10.0)), plan.shopping());
        assertTrue(plan.component(COMPONENT).orElseThrow().built());
        assertTrue(plan.component(ALLOY).orElseThrow().reaction());
        assertEquals(1, plan.component(ALLOY).orElseThrow().runs());
        assertEquals(Duration.ofSeconds(180), plan.componentTime());
        assertEquals(Duration.ofSeconds(3600), plan.reactionTime());
    }

    @Test
    void aChoiceToBuyStopsTheChainThere() {
        Plan plan = BuildPlanner.plan(THREE_COMPONENTS, setup(true, true, Map.of(ALLOY, false)), 1, 1);

        assertEquals(150, plan.materialsCost(), 1e-9);
        assertEquals(List.of(new MaterialLine(ALLOY, 30, 5.0)), plan.shopping());
        assertEquals(Duration.ZERO, plan.reactionTime());
    }

    @Test
    void withoutBuildingEverythingIsBoughtButTheBuildCostIsStillShown() {
        Plan plan = BuildPlanner.plan(THREE_COMPONENTS, setup(true, false, Map.of()), 1, 1);

        assertEquals(3000, plan.materialsCost(), 1e-9);
        assertEquals(List.of(new MaterialLine(COMPONENT, 3, 1000.0)), plan.shopping());
        assertEquals(1, plan.components().size());
        assertFalse(plan.component(COMPONENT).orElseThrow().built());
        assertEquals(50, plan.component(COMPONENT).orElseThrow().buildPrice(), 1e-9);
    }

    @Test
    void reactionsOutsideLowAndNullSecAreBought() {
        Plan plan = BuildPlanner.plan(THREE_COMPONENTS, setup(false, true, Map.of()), 1, 1);

        assertEquals(150, plan.materialsCost(), 1e-9);
        assertTrue(plan.component(ALLOY).isEmpty());
    }

    @Test
    void theWholeChainIsFoundForPricing() {
        assertEquals(Set.of(COMPONENT, ALLOY, MOON_GOO, FUEL), BuildPlanner.typesInvolved(List.of(COMPONENT),
                type -> Optional.ofNullable(PRODUCERS.get(type))));
    }
}
