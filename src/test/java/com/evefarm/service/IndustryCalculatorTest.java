package com.evefarm.service;

import com.evefarm.model.Decryptor;
import com.evefarm.model.IndustryActivity;
import com.evefarm.model.SkillRequirement;
import com.evefarm.model.SolarSystem;
import com.evefarm.model.TypeQuantity;
import com.evefarm.service.IndustryCalculator.Facility;
import com.evefarm.service.IndustryCalculator.Invention;
import com.evefarm.service.IndustryCalculator.Jobs;
import com.evefarm.service.IndustryCalculator.Manufacturing;
import com.evefarm.service.IndustryCalculator.Rig;
import com.evefarm.service.IndustryCalculator.Security;
import com.evefarm.service.IndustryCalculator.Structure;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndustryCalculatorTest {

    private static final int TRITANIUM = 34;
    private static final int PYERITE = 35;
    private static final int INDUSTRY = 3380;
    private static final int ADVANCED_INDUSTRY = 3388;
    private static final int ADVANCED_SMALL_SHIP_CONSTRUCTION = 3395;
    private static final int MECHANICAL_ENGINEERING = 11452;
    private static final int MINMATAR_STARSHIP_ENGINEERING = 11445;
    private static final int MINMATAR_ENCRYPTION_METHODS = 21791;
    private static final int MUTAGENIC_STABILIZATION = 81896;
    private static final int MASS_PRODUCTION = 3387;
    private static final int ADVANCED_MASS_PRODUCTION = 24625;
    private static final int ACCOUNTING = 16622;
    private static final int BEANCOUNTER_BX_804 = 27171;
    private static final Facility NPC_STATION = new Facility(Structure.NPC_STATION, Rig.T2, Rig.T2, Security.NULL, 0.1);
    private static final IndustryActivity RIFTER = new IndustryActivity(691, IndustryActivity.MANUFACTURING, 6000,
            List.of(new TypeQuantity(TRITANIUM, 32000), new TypeQuantity(PYERITE, 1)),
            List.of(new TypeQuantity(587, 1)), Map.of(), List.of());

    private static IntUnaryOperator levels(Map<Integer, Integer> levels) {
        return skill -> levels.getOrDefault(skill, 0);
    }

    @Test
    void materialEfficiencyNeverDropsBelowOnePerRun() {
        assertEquals(28_800, IndustryCalculator.materialQuantity(32_000, 1, 10, 1.0));
        assertEquals(287_712, IndustryCalculator.materialQuantity(32_000, 10, 10, 0.999));
        assertEquals(10, IndustryCalculator.materialQuantity(1, 10, 10, 0.94));
        assertEquals(1, IndustryCalculator.materialQuantity(1, 1, 10, 0.94));
    }

    @Test
    void structuresAndRigsCountOnlyOutsideNpcStations() {
        Facility raitaru = new Facility(Structure.RAITARU, Rig.T2, Rig.T1, Security.NULL, 0.05);

        assertEquals(0.99 * (1 - 0.024 * 2.1), raitaru.materialMultiplier(), 1e-12);
        assertEquals(0.85 * (1 - 0.20 * 2.1), raitaru.timeMultiplier(), 1e-12);
        assertEquals(0.05, raitaru.tax());
        assertEquals(1.0, NPC_STATION.materialMultiplier());
        assertEquals(1.0, NPC_STATION.timeMultiplier());
        assertEquals(IndustryCalculator.NPC_STATION_TAX, NPC_STATION.tax());
    }

    @Test
    void theJobFeeIsTheIndexTaxAndSurchargeOnTheItemValue() {
        Facility azbel = new Facility(Structure.AZBEL, Rig.NONE, Rig.NONE, Security.HIGH, 0.01);

        assertEquals(1_000_000 * (0.05 * 0.96 + 0.01 + 0.04), azbel.jobFee(1_000_000, 0.05), 1e-6);
    }

    @Test
    void scienceJobsPayOnTwoPercentOfTheProductValue() {
        Facility azbel = new Facility(Structure.AZBEL, Rig.NONE, Rig.NONE, Security.HIGH, 0.01);

        assertEquals(0.02 * 32_000 * 3.5 * (0.05 * 0.96 + 0.01 + 0.04), IndustryCalculator.scienceJobFee(azbel,
                List.of(new TypeQuantity(TRITANIUM, 32_000)), 0.05, Map.of(TRITANIUM, 3.5)), 1e-6);
    }

    @Test
    void securityDecidesTheRigBonus() {
        assertEquals(Security.HIGH, Security.of(0.5));
        assertEquals(Security.LOW, Security.of(0.4));
        assertEquals(Security.LOW, Security.of(0.1));
        assertEquals(Security.NULL, Security.of(0.0));
        assertEquals(Security.NULL, Security.of(-0.7));
        assertEquals(0.5, new SolarSystem(1, "A", 0.45, null).roundedSecurity());
        assertEquals(0.1, new SolarSystem(2, "B", 0.03, null).roundedSecurity());
        assertEquals(0.0, new SolarSystem(3, "C", -0.04, null).roundedSecurity());
    }

    @Test
    void skillsShortenTheBuild() {
        List<SkillRequirement> jaguar = List.of(new SkillRequirement(INDUSTRY, 5),
                new SkillRequirement(ADVANCED_SMALL_SHIP_CONSTRUCTION, 1),
                new SkillRequirement(MINMATAR_STARSHIP_ENGINEERING, 1),
                new SkillRequirement(MECHANICAL_ENGINEERING, 1));
        IntUnaryOperator trained = levels(Map.of(INDUSTRY, 5, ADVANCED_INDUSTRY, 5,
                ADVANCED_SMALL_SHIP_CONSTRUCTION, 4, MECHANICAL_ENGINEERING, 3));

        assertEquals(0.8 * 0.85, IndustryCalculator.skillTimeMultiplier(List.of(), trained), 1e-12);
        assertEquals(0.8 * 0.85 * 0.96 * 0.97, IndustryCalculator.skillTimeMultiplier(jaguar, trained), 1e-12);
        assertEquals(0.9, IndustryCalculator.skillTimeMultiplier(
                List.of(new SkillRequirement(MUTAGENIC_STABILIZATION, 1)), levels(Map.of(MUTAGENIC_STABILIZATION, 5))),
                1e-12);
        assertEquals(0.96, IndustryCalculator.implantTimeMultiplier(List.of(BEANCOUNTER_BX_804, 9999)), 1e-12);
        assertEquals(1.0, IndustryCalculator.implantTimeMultiplier(List.of()));
    }

    @Test
    void skillsDecideTheLinesTheSalesTaxAndWhatCanBeBuilt() {
        IntUnaryOperator trained = levels(Map.of(MASS_PRODUCTION, 5, ADVANCED_MASS_PRODUCTION, 4, ACCOUNTING, 5,
                INDUSTRY, 4));

        assertEquals(10, IndustryCalculator.manufacturingLines(trained));
        assertEquals(1, IndustryCalculator.manufacturingLines(levels(Map.of())));
        assertEquals(0.075 * 0.45, IndustryCalculator.salesTax(trained), 1e-12);
        assertEquals(0.075, IndustryCalculator.salesTax(levels(Map.of())), 1e-12);
        assertTrue(IndustryCalculator.hasSkills(List.of(new SkillRequirement(INDUSTRY, 4)), trained));
        assertFalse(IndustryCalculator.hasSkills(List.of(new SkillRequirement(INDUSTRY, 5)), trained));
    }

    @Test
    void aJobAddsUpMaterialsFeeUnitsAndTime() {
        Manufacturing built = IndustryCalculator.manufacture(RIFTER, new Jobs(10, 0, 1), 10, 20, NPC_STATION, 0.02,
                0.68, Map.of(TRITANIUM, 4.0, PYERITE, 10.0), Map.of(TRITANIUM, 3.5, PYERITE, 8.0));

        assertEquals(288_000, built.materials().get(0).quantity());
        assertEquals(10, built.materials().get(1).quantity());
        assertEquals(288_000 * 4.0 + 10 * 10.0, built.materialsCost(), 1e-6);
        assertEquals(320_000 * 3.5 + 10 * 8.0, built.estimatedItemValue(), 1e-6);
        assertEquals(built.estimatedItemValue() * (0.02 + 0.0025 + 0.04), built.jobFee(), 1e-6);
        assertEquals(10, built.units());
        assertEquals(1, built.jobs());
        assertEquals(Duration.ofSeconds(Math.round(6000 * 10 * 0.8 * 0.68)), built.time());
    }

    @Test
    void eachInventedCopyIsItsOwnJobAndJobsShareTheLines() {
        IndustryActivity frigate = new IndustryActivity(2, IndustryActivity.MANUFACTURING, 1000,
                List.of(new TypeQuantity(TRITANIUM, 4)), List.of(new TypeQuantity(3, 1)), Map.of(), List.of());
        Map<Integer, Double> prices = Map.of(TRITANIUM, 1.0);

        Manufacturing oneJob = IndustryCalculator.manufacture(frigate, new Jobs(10, 0, 9), 4, 0, NPC_STATION, 0, 1,
                prices, prices);
        Manufacturing tenJobs = IndustryCalculator.manufacture(frigate, new Jobs(10, 1, 9), 4, 0, NPC_STATION, 0, 1,
                prices, prices);

        assertEquals(39, oneJob.materials().getFirst().quantity());
        assertEquals(40, tenJobs.materials().getFirst().quantity());
        assertEquals(10, tenJobs.jobs());
        assertEquals(Duration.ofSeconds(10_000), oneJob.time());
        assertEquals(Duration.ofSeconds(2_000), tenJobs.time());
        assertEquals(oneJob.jobFee(), tenJobs.jobFee(), 1e-9);
        assertEquals(Duration.ofSeconds(10_000), IndustryCalculator.manufacture(frigate, new Jobs(25, 10, 9), 0, 0,
                NPC_STATION, 0, 1, prices, prices).time());
        assertEquals(Duration.ofSeconds(15_000), IndustryCalculator.manufacture(frigate, new Jobs(25, 10, 2), 0, 0,
                NPC_STATION, 0, 1, prices, prices).time());
        assertEquals(3, new Jobs(25, 10, 2).count());
    }

    @Test
    void inventionChanceGrowsWithSkillsAndDecryptors() {
        assertEquals(0.3 * (1 + 8 / 30.0 + 4 / 40.0), IndustryCalculator.inventionChance(0.3, 8, 4, 1.0), 1e-12);
        assertEquals(1.0, IndustryCalculator.inventionChance(0.9, 10, 5, 1.9));
    }

    @Test
    void aDecryptorChangesTheInventedCopy() {
        IndustryActivity invention = new IndustryActivity(1, IndustryActivity.INVENTION, 60000,
                List.of(new TypeQuantity(20171, 2)), List.of(new TypeQuantity(11401, 10)), Map.of(11401, 0.3),
                List.of(new SkillRequirement(MECHANICAL_ENGINEERING, 1),
                        new SkillRequirement(MINMATAR_STARSHIP_ENGINEERING, 1),
                        new SkillRequirement(MINMATAR_ENCRYPTION_METHODS, 1)));
        IntUnaryOperator trained = levels(Map.of(MECHANICAL_ENGINEERING, 5, MINMATAR_STARSHIP_ENGINEERING, 3,
                MINMATAR_ENCRYPTION_METHODS, 4));
        Decryptor accelerant = new Decryptor(34201, "Accelerant Decryptor", 1.2, 2, 10, 1);

        Invention plain = IndustryCalculator.invent(invention, 11401, null, trained, 100_000, 0, 20_000, 1_000);
        Invention boosted = IndustryCalculator.invent(invention, 11401, accelerant, trained, 100_000, 500_000, 20_000,
                1_000);

        assertEquals(IndustryCalculator.inventionChance(0.3, 8, 4, 1.0), plain.chance(), 1e-12);
        assertEquals(10, plain.runsPerCopy());
        assertEquals(2, plain.me());
        assertEquals(4, plain.te());
        assertEquals(121_000 / (plain.chance() * 10), plain.costPerRun(), 1e-6);
        assertEquals(11, boosted.runsPerCopy());
        assertEquals(4, boosted.me());
        assertEquals(14, boosted.te());
        assertEquals(plain.chance() * 1.2, boosted.chance(), 1e-12);
        assertEquals(621_000 / (boosted.chance() * 11), boosted.costPerRun(), 1e-6);
    }

    @Test
    void sellingPaysTaxAndBroker() {
        IndustryCalculator.Sale sale = IndustryCalculator.sell(10, 1_000_000, 0.036, 0.015);

        assertEquals(10_000_000, sale.grossValue(), 1e-6);
        assertEquals(510_000, sale.fees(), 1e-6);
        assertEquals(9_490_000, sale.net(), 1e-6);
        assertEquals(33.75 + IndustryCalculator.MIN_BROKER_FEE,
                IndustryCalculator.sell(1, 1_000, 0.03375, 0.015).fees(), 1e-9);
        assertEquals(33.75, IndustryCalculator.sell(1, 1_000, 0.03375, 0).fees(), 1e-9);
        assertEquals(2_000_000, IndustryCalculator.iskPerHour(1_000_000, Duration.ofMinutes(30)), 1e-6);
    }
}
