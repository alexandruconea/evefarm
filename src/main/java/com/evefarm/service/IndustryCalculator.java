package com.evefarm.service;

import com.evefarm.model.Decryptor;
import com.evefarm.model.IndustryActivity;
import com.evefarm.model.SkillRequirement;
import com.evefarm.model.TypeQuantity;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;

public final class IndustryCalculator {

    public static final double SCC_SURCHARGE = 0.04;
    public static final double NPC_STATION_TAX = 0.0025;
    public static final double BASE_SALES_TAX = 0.075;
    public static final double MIN_BROKER_FEE = 100;
    public static final int INVENTED_BASE_ME = 2;
    public static final int INVENTED_BASE_TE = 4;
    public static final int MAX_ME = 10;
    public static final int MAX_TE = 20;
    private static final double SCIENCE_JOB_SHARE = 0.02;
    private static final int INDUSTRY = 3380;
    private static final int ADVANCED_INDUSTRY = 3388;
    private static final int MASS_PRODUCTION = 3387;
    private static final int ADVANCED_MASS_PRODUCTION = 24625;
    private static final int ACCOUNTING = 16622;
    private static final int REACTIONS = 45746;
    private static final int MASS_REACTIONS = 45748;
    private static final int ADVANCED_MASS_REACTIONS = 45749;
    private static final int MUTAGENIC_STABILIZATION = 81896;
    private static final Map<Integer, Double> FASTER_BUILD_IMPLANTS = Map.of(27170, 0.01, 27167, 0.02, 27171, 0.04);
    private static final Set<Integer> ONE_PERCENT_FASTER_SKILLS = Set.of(3395, 3396, 3397, 3398, 3400, 11433, 11441,
            11442, 11443, 11444, 11445, 11446, 11447, 11448, 11449, 11450, 11451, 11452, 11453, 11454, 11455, 11529,
            52307, 77725, 81050);
    private static final Set<Integer> ENCRYPTION_SKILLS = Set.of(3408, 21790, 21791, 23087, 23121, 52308, 55025);

    public enum Structure {
        NPC_STATION("NPC station", 1.0, 1.0, 1.0),
        RAITARU("Raitaru", 0.99, 0.85, 0.97),
        AZBEL("Azbel", 0.99, 0.80, 0.96),
        SOTIYO("Sotiyo", 0.99, 0.70, 0.95),
        REFINERY("Refinery", 1.0, 1.0, 1.0);

        public static final List<Structure> BUILD_SITES = List.of(NPC_STATION, RAITARU, AZBEL, SOTIYO);

        private final String label;
        private final double materialMultiplier;
        private final double timeMultiplier;
        private final double costMultiplier;

        Structure(String label, double materialMultiplier, double timeMultiplier, double costMultiplier) {
            this.label = label;
            this.materialMultiplier = materialMultiplier;
            this.timeMultiplier = timeMultiplier;
            this.costMultiplier = costMultiplier;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum Rig {
        NONE("No rig", 0, 0),
        T1("T1 rig", 0.02, 0.20),
        T2("T2 rig", 0.024, 0.24);

        private final String label;
        private final double materialBonus;
        private final double timeBonus;

        Rig(String label, double materialBonus, double timeBonus) {
            this.label = label;
            this.materialBonus = materialBonus;
            this.timeBonus = timeBonus;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum Security {
        HIGH(1.0),
        LOW(1.9),
        NULL(2.1);

        private final double rigMultiplier;

        Security(double rigMultiplier) {
            this.rigMultiplier = rigMultiplier;
        }

        public static Security of(double roundedSecurity) {
            if (roundedSecurity >= 0.5) {
                return HIGH;
            }
            return roundedSecurity > 0 ? LOW : NULL;
        }
    }

    public record Facility(Structure structure, Rig materialRig, Rig timeRig, Security security, double taxRate) {

        public double materialMultiplier() {
            return structure.materialMultiplier * (1 - rigBonus(materialRig.materialBonus));
        }

        public double timeMultiplier() {
            return structure.timeMultiplier * (1 - rigBonus(timeRig.timeBonus));
        }

        private double rigBonus(double bonus) {
            return structure == Structure.NPC_STATION ? 0 : bonus * security.rigMultiplier;
        }

        public double tax() {
            return structure == Structure.NPC_STATION ? NPC_STATION_TAX : taxRate;
        }

        public double jobFee(double estimatedItemValue, double costIndex) {
            return estimatedItemValue * (costIndex * structure.costMultiplier + tax() + SCC_SURCHARGE);
        }
    }

    public record MaterialLine(int typeId, long quantity, double unitPrice) {

        public double total() {
            return quantity * unitPrice;
        }
    }

    public record Jobs(int runs, int runsPerJob, int lines) {

        public int runsPerFullJob() {
            return Math.max(1, runsPerJob > 0 ? Math.min(runs, runsPerJob) : runs);
        }

        public int fullJobs() {
            return runs / runsPerFullJob();
        }

        public int lastJobRuns() {
            return runs % runsPerFullJob();
        }

        public int count() {
            return fullJobs() + (lastJobRuns() > 0 ? 1 : 0);
        }
    }

    public record Manufacturing(List<MaterialLine> materials, double materialsCost, double estimatedItemValue,
                                double jobFee, long units, int jobs, Duration time) {

        public double cost() {
            return materialsCost + jobFee;
        }
    }

    public record Invention(Decryptor decryptor, double chance, int runsPerCopy, int me, int te,
                            double attemptCost, double costPerRun) {
    }

    public record Sale(double grossValue, double fees) {

        public double net() {
            return grossValue - fees;
        }
    }

    public static long materialQuantity(long baseQuantity, int runs, int me, double facilityMultiplier) {
        double adjusted = baseQuantity * runs * (1 - me / 100.0) * facilityMultiplier;
        return Math.max(runs, (long) Math.ceil(Math.round(adjusted * 100) / 100.0));
    }

    public static double estimatedItemValue(List<TypeQuantity> materials, int runs,
                                            Map<Integer, Double> adjustedPrices) {
        return materials.stream()
                .mapToDouble(material -> material.quantity() * runs
                        * adjustedPrices.getOrDefault(material.typeId(), 0.0))
                .sum();
    }

    public static double skillTimeMultiplier(List<SkillRequirement> requiredSkills, IntUnaryOperator level) {
        double multiplier = (1 - 0.04 * level.applyAsInt(INDUSTRY)) * (1 - 0.03 * level.applyAsInt(ADVANCED_INDUSTRY));
        for (SkillRequirement skill : requiredSkills) {
            multiplier *= 1 - timeBonusPerLevel(skill.skillId()) * level.applyAsInt(skill.skillId());
        }
        return multiplier;
    }

    private static double timeBonusPerLevel(int skillId) {
        if (skillId == MUTAGENIC_STABILIZATION) {
            return 0.02;
        }
        return ONE_PERCENT_FASTER_SKILLS.contains(skillId) ? 0.01 : 0;
    }

    public static double implantTimeMultiplier(List<Integer> implants) {
        double multiplier = 1;
        for (int implant : implants) {
            multiplier *= 1 - FASTER_BUILD_IMPLANTS.getOrDefault(implant, 0.0);
        }
        return multiplier;
    }

    public static int manufacturingLines(IntUnaryOperator level) {
        return 1 + level.applyAsInt(MASS_PRODUCTION) + level.applyAsInt(ADVANCED_MASS_PRODUCTION);
    }

    public static double reactionTimeMultiplier(IntUnaryOperator level) {
        return 1 - 0.04 * level.applyAsInt(REACTIONS);
    }

    public static int reactionLines(IntUnaryOperator level) {
        return 1 + level.applyAsInt(MASS_REACTIONS) + level.applyAsInt(ADVANCED_MASS_REACTIONS);
    }

    public static double salesTax(IntUnaryOperator level) {
        return BASE_SALES_TAX * (1 - 0.11 * level.applyAsInt(ACCOUNTING));
    }

    public static boolean hasSkills(List<SkillRequirement> requiredSkills, IntUnaryOperator level) {
        return requiredSkills.stream().allMatch(skill -> level.applyAsInt(skill.skillId()) >= skill.level());
    }

    public static double scienceJobFee(Facility facility, List<TypeQuantity> productMaterials, double costIndex,
                                       Map<Integer, Double> adjustedPrices) {
        return facility.jobFee(SCIENCE_JOB_SHARE * estimatedItemValue(productMaterials, 1, adjustedPrices), costIndex);
    }

    public static Manufacturing manufacture(IndustryActivity activity, Jobs jobs, int me, int te, Facility facility,
                                            double costIndex, double timeMultiplier, Map<Integer, Double> prices,
                                            Map<Integer, Double> adjustedPrices) {
        List<MaterialLine> lines = new ArrayList<>();
        double materialsCost = 0;
        for (TypeQuantity material : activity.materials()) {
            long quantity = jobs.fullJobs() * materialQuantity(material.quantity(), jobs.runsPerFullJob(), me,
                    facility.materialMultiplier());
            if (jobs.lastJobRuns() > 0) {
                quantity += materialQuantity(material.quantity(), jobs.lastJobRuns(), me,
                        facility.materialMultiplier());
            }
            MaterialLine line = new MaterialLine(material.typeId(), quantity,
                    prices.getOrDefault(material.typeId(), 0.0));
            lines.add(line);
            materialsCost += line.total();
        }
        double estimatedItemValue = estimatedItemValue(activity.materials(), jobs.runs(), adjustedPrices);
        TypeQuantity product = activity.product();
        long units = product == null ? 0 : product.quantity() * jobs.runs();
        double secondsPerRun = activity.timeSeconds() * (1 - te / 100.0) * facility.timeMultiplier() * timeMultiplier;
        return new Manufacturing(List.copyOf(lines), materialsCost, estimatedItemValue,
                facility.jobFee(estimatedItemValue, costIndex), units, jobs.count(),
                Duration.ofSeconds(Math.round(elapsedSeconds(jobs, secondsPerRun))));
    }

    private static double elapsedSeconds(Jobs jobs, double secondsPerRun) {
        int lines = Math.max(1, jobs.lines());
        int rounds = (jobs.fullJobs() + lines - 1) / lines;
        double seconds = rounds * jobs.runsPerFullJob() * secondsPerRun;
        if (jobs.lastJobRuns() > 0 && jobs.fullJobs() % lines == 0) {
            seconds += jobs.lastJobRuns() * secondsPerRun;
        }
        return seconds;
    }

    public static double inventionChance(double baseProbability, int scienceLevels, int encryptionLevel,
                                         double decryptorMultiplier) {
        return Math.min(1.0, baseProbability * (1 + scienceLevels / 30.0 + encryptionLevel / 40.0)
                * decryptorMultiplier);
    }

    public static Invention invent(IndustryActivity invention, int inventedBlueprintId, Decryptor decryptor,
                                   IntUnaryOperator level, double datacoresCost, double decryptorPrice,
                                   double inventionFee, double copyFee) {
        int science = 0;
        int encryption = 0;
        for (SkillRequirement skill : invention.skills()) {
            if (ENCRYPTION_SKILLS.contains(skill.skillId())) {
                encryption += level.applyAsInt(skill.skillId());
            } else {
                science += level.applyAsInt(skill.skillId());
            }
        }
        long baseRuns = invention.products().stream()
                .filter(product -> product.typeId() == inventedBlueprintId)
                .mapToLong(TypeQuantity::quantity).findFirst().orElse(1);
        double multiplier = decryptor == null ? 1.0 : decryptor.probabilityMultiplier();
        double chance = inventionChance(invention.probability(inventedBlueprintId), science, encryption, multiplier);
        int runs = (int) Math.max(1, baseRuns + (decryptor == null ? 0 : decryptor.runsModifier()));
        int me = INVENTED_BASE_ME + (decryptor == null ? 0 : decryptor.meModifier());
        int te = INVENTED_BASE_TE + (decryptor == null ? 0 : decryptor.teModifier());
        double attemptCost = datacoresCost + (decryptor == null ? 0 : decryptorPrice) + inventionFee + copyFee;
        double costPerRun = chance > 0 ? attemptCost / (chance * runs) : Double.POSITIVE_INFINITY;
        return new Invention(decryptor, chance, runs, me, te, attemptCost, costPerRun);
    }

    public static Sale sell(long units, double unitPrice, double salesTax, double brokerFee) {
        double gross = units * unitPrice;
        double broker = gross > 0 && brokerFee > 0 ? Math.max(MIN_BROKER_FEE, gross * brokerFee) : 0;
        return new Sale(gross, gross * salesTax + broker);
    }

    public static double iskPerHour(double profit, Duration time) {
        double hours = time.toSeconds() / 3600.0;
        return hours > 0 ? profit / hours : 0;
    }

    private IndustryCalculator() {
    }
}
