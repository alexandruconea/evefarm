package com.evefarm.service;

import com.evefarm.model.CharacterSkills;
import com.evefarm.model.Decryptor;
import com.evefarm.model.IndustryActivity;
import com.evefarm.model.OwnedSkill;
import com.evefarm.model.SolarSystem;
import com.evefarm.model.TypeQuantity;
import com.evefarm.service.BuildPlanner.Plan;
import com.evefarm.service.IndustryCalculator.Facility;
import com.evefarm.service.IndustryCalculator.Invention;
import com.evefarm.service.IndustryCalculator.Jobs;
import com.evefarm.service.IndustryCalculator.Manufacturing;
import com.evefarm.service.IndustryCalculator.Rig;
import com.evefarm.service.IndustryCalculator.Sale;
import com.evefarm.service.IndustryCalculator.Security;
import com.evefarm.service.IndustryCalculator.Structure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;

public final class IndustryService {

    private static final int ASSUMED_LEVEL = 4;

    public enum Stock {
        NONE("Don't use"),
        SYSTEM("In the build system"),
        ANYWHERE("Anywhere");

        private final String label;

        Stock(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public record Settings(int blueprintId, Long characterId, int runs, int me, int te, boolean includeInvention,
                           String systemName, Structure structure, Rig materialRig, Rig timeRig, double facilityTax,
                           double brokerFee, BuildPlanner.Mode buildMode, int componentMe, int componentTe,
                           Map<Integer, Boolean> buildChoices, BuildPlanner.Surplus surplus, Stock stock) {
    }

    public record SkillEffects(boolean assumed, boolean canBuild, boolean canInvent, int lines, double salesTax) {
    }

    public record Option(Decryptor decryptor, Invention invention, Manufacturing manufacturing, Plan plan,
                         double inventionCost, double totalCost, Sale sale, double profit, double iskPerHour,
                         Plan stocked, double toSpend) {

        public double unitCost() {
            return manufacturing.units() > 0 ? totalCost / manufacturing.units() : 0;
        }

        public double unitProfit() {
            return manufacturing.units() > 0 ? profit / manufacturing.units() : 0;
        }
    }

    public record Result(int productId, String productName, SolarSystem system, double manufacturingIndex,
                         SkillEffects skills, boolean inventable, boolean invented, boolean reactionsAllowed,
                         double productPrice, Map<Integer, String> names, List<Option> options, int bestIndex) {
    }

    private final IndustryCatalogService industryCatalogService;
    private final IndustryMarketService industryMarketService;
    private final PriceService priceService;
    private final SkillService skillService;
    private final AssetService assetService;

    public IndustryService(IndustryCatalogService industryCatalogService, IndustryMarketService industryMarketService,
                           PriceService priceService, SkillService skillService, AssetService assetService) {
        this.industryCatalogService = industryCatalogService;
        this.industryMarketService = industryMarketService;
        this.priceService = priceService;
        this.skillService = skillService;
        this.assetService = assetService;
    }

    public Result calculate(Settings settings) {
        IndustryActivity manufacturing = industryCatalogService.activity(settings.blueprintId(),
                IndustryActivity.MANUFACTURING).orElseThrow(() -> new IllegalStateException("Unknown blueprint"));
        TypeQuantity product = manufacturing.product();
        if (product == null) {
            throw new IllegalStateException("The blueprint makes nothing");
        }
        SolarSystem system = industryCatalogService.findSystem(settings.systemName()).orElse(null);
        Facility facility = new Facility(settings.structure(), settings.materialRig(), settings.timeRig(),
                system == null ? Security.HIGH : Security.of(system.roundedSecurity()), settings.facilityTax());
        CharacterSkills character = characterSkills(settings.characterId());
        Map<Integer, Integer> levels = activeLevels(character);
        IntUnaryOperator level = skillId -> levels == null ? ASSUMED_LEVEL : levels.getOrDefault(skillId, 0);

        IndustryActivity inventionActivity = industryCatalogService.inventedFrom(settings.blueprintId())
                .flatMap(source -> industryCatalogService.activity(source, IndustryActivity.INVENTION))
                .orElse(null);
        IndustryActivity invention = settings.includeInvention() ? inventionActivity : null;
        List<Decryptor> decryptors = invention == null ? List.of() : industryCatalogService.decryptors();
        SkillEffects skills = new SkillEffects(levels == null,
                levels == null || IndustryCalculator.hasSkills(manufacturing.skills(), level),
                levels == null || invention == null || IndustryCalculator.hasSkills(invention.skills(), level),
                IndustryCalculator.manufacturingLines(level), IndustryCalculator.salesTax(level));

        Map<Integer, Optional<IndustryActivity>> producers = new HashMap<>();
        Function<Integer, Optional<IndustryActivity>> producer =
                typeId -> producers.computeIfAbsent(typeId, industryCatalogService::producedBy);
        Set<Integer> typeIds = new LinkedHashSet<>();
        typeIds.add(product.typeId());
        typeIds.addAll(BuildPlanner.typesInvolved(
                manufacturing.materials().stream().map(TypeQuantity::typeId).toList(), producer));
        if (invention != null) {
            invention.materials().forEach(material -> typeIds.add(material.typeId()));
            decryptors.forEach(decryptor -> typeIds.add(decryptor.typeId()));
        }
        priceService.ensureFreshPrices(typeIds);
        Map<Integer, Double> prices = priceService.getUnitPrices();
        Map<Integer, Double> adjusted = industryMarketService.adjustedPrices();
        double manufacturingIndex = costIndex(system, "manufacturing");
        double timeMultiplier = IndustryCalculator.skillTimeMultiplier(manufacturing.skills(), level)
                * IndustryCalculator.implantTimeMultiplier(character == null ? List.of() : character.implants());
        double productPrice = prices.getOrDefault(product.typeId(), 0.0);
        boolean reactionsAllowed = system != null && facility.security() != Security.HIGH;
        BuildPlanner.Setup setup = new BuildPlanner.Setup(producer, prices, adjusted,
                new Facility(settings.structure(), Rig.NONE, Rig.NONE, facility.security(), settings.facilityTax()),
                new Facility(Structure.REFINERY, Rig.NONE, Rig.NONE, facility.security(), settings.facilityTax()),
                manufacturingIndex, costIndex(system, "reaction"), settings.componentMe(), settings.componentTe(),
                level, reactionsAllowed, settings.buildMode(), settings.buildChoices(), settings.surplus(),
                skills.salesTax() + settings.brokerFee());
        int reactionLines = IndustryCalculator.reactionLines(level);
        Map<Integer, Long> stock = switch (settings.stock()) {
            case NONE -> Map.of();
            case SYSTEM -> system == null ? Map.of() : assetService.usableStock(system.systemId());
            case ANYWHERE -> assetService.usableStock(null);
        };

        List<Option> options = new ArrayList<>();
        if (invention == null) {
            Manufacturing built = IndustryCalculator.manufacture(manufacturing,
                    new Jobs(settings.runs(), 0, skills.lines()), settings.me(), settings.te(), facility,
                    manufacturingIndex, timeMultiplier, prices, adjusted);
            Plan plan = BuildPlanner.plan(built.materials(), setup, skills.lines(), reactionLines);
            Plan stocked = BuildPlanner.withStock(built.materials(), setup, plan, stock, skills.lines(),
                    reactionLines);
            options.add(option(null, null, built, plan, stocked, 0, productPrice, skills.salesTax(), settings));
        } else {
            double datacores = invention.materials().stream()
                    .mapToDouble(material -> material.quantity() * prices.getOrDefault(material.typeId(), 0.0))
                    .sum();
            double inventionFee = IndustryCalculator.scienceJobFee(facility, manufacturing.materials(),
                    costIndex(system, "invention"), adjusted);
            double copyFee = industryCatalogService.activity(invention.blueprintId(), IndustryActivity.MANUFACTURING)
                    .map(original -> IndustryCalculator.scienceJobFee(facility, original.materials(),
                            costIndex(system, "copying"), adjusted))
                    .orElse(0.0);
            List<Decryptor> choices = new ArrayList<>();
            choices.add(null);
            choices.addAll(decryptors);
            for (Decryptor decryptor : choices) {
                double decryptorPrice = decryptor == null ? 0 : prices.getOrDefault(decryptor.typeId(), 0.0);
                Invention invented = IndustryCalculator.invent(invention, settings.blueprintId(), decryptor, level,
                        datacores, decryptorPrice, inventionFee, copyFee);
                Manufacturing built = IndustryCalculator.manufacture(manufacturing,
                        new Jobs(settings.runs(), invented.runsPerCopy(), skills.lines()), invented.me(),
                        invented.te(), facility, manufacturingIndex, timeMultiplier, prices, adjusted);
                Plan plan = BuildPlanner.plan(built.materials(), setup, skills.lines(), reactionLines);
                Plan stocked = BuildPlanner.withStock(built.materials(), setup, plan, stock, skills.lines(),
                        reactionLines);
                options.add(option(decryptor, invented, built, plan, stocked, invented.costPerRun() * settings.runs(),
                        productPrice, skills.salesTax(), settings));
            }
        }
        int best = 0;
        for (int i = 1; i < options.size(); i++) {
            if (options.get(i).profit() > options.get(best).profit()) {
                best = i;
            }
        }
        Map<Integer, String> names = industryCatalogService.names(typeIds);
        return new Result(product.typeId(), names.getOrDefault(product.typeId(), "Type #" + product.typeId()), system,
                manufacturingIndex, skills, inventionActivity != null, invention != null, reactionsAllowed,
                productPrice, names, List.copyOf(options), best);
    }

    private double costIndex(SolarSystem system, String activity) {
        return system == null ? 0 : industryMarketService.costIndex(system.systemId(), activity);
    }

    private static Option option(Decryptor decryptor, Invention invention, Manufacturing built, Plan plan,
                                 Plan stocked, double inventionCost, double productPrice, double salesTax,
                                 Settings settings) {
        Sale sale = IndustryCalculator.sell(built.units(), productPrice, salesTax, settings.brokerFee());
        double totalCost = plan.materialsCost() + built.jobFee() + inventionCost;
        double profit = sale.net() - totalCost;
        double toSpend = stocked.purchaseCost() + stocked.jobFees() + built.jobFee() + inventionCost;
        return new Option(decryptor, invention, built, plan, inventionCost, totalCost, sale, profit,
                IndustryCalculator.iskPerHour(profit, built.time().plus(plan.extraTime())), stocked, toSpend);
    }

    private CharacterSkills characterSkills(Long characterId) {
        return characterId == null ? null : skillService.skills(characterId).orElse(null);
    }

    private static Map<Integer, Integer> activeLevels(CharacterSkills character) {
        if (character == null) {
            return null;
        }
        Map<Integer, Integer> levels = new HashMap<>();
        for (OwnedSkill skill : character.skills()) {
            levels.put(skill.skillId(), skill.activeLevel());
        }
        return levels;
    }
}
