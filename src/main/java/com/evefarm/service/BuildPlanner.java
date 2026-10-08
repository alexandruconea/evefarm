package com.evefarm.service;

import com.evefarm.model.IndustryActivity;
import com.evefarm.model.TypeQuantity;
import com.evefarm.service.IndustryCalculator.Facility;
import com.evefarm.service.IndustryCalculator.MaterialLine;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;

public final class BuildPlanner {

    private static final int MAX_DEPTH = 8;
    private static final int MAX_ROUNDS = 6;

    public enum Mode {
        BUY("Buy all"),
        CHEAPER("Build when cheaper"),
        ALL("Build all from reactions");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum Surplus {
        KEEP("Keep it for later builds"),
        SELL("Sell it"),
        WASTE("Count it as waste");

        private final String label;

        Surplus(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public record Setup(Function<Integer, Optional<IndustryActivity>> producer, Map<Integer, Double> prices,
                        Map<Integer, Double> adjustedPrices, Facility componentFacility, Facility reactionFacility,
                        double manufacturingIndex, double reactionIndex, int componentMe, int componentTe,
                        IntUnaryOperator level, boolean reactionsAllowed, Mode mode,
                        Map<Integer, Boolean> choices, Surplus surplus, double sellFees) {
    }

    public record Component(int typeId, boolean reaction, long needed, int runs, long produced, double marketPrice,
                            double buildPrice, boolean built, Duration time, int step, List<TypeQuantity> inputs) {

        public long surplus() {
            return built ? produced - needed : 0;
        }
    }

    public record Step(List<Component> jobs, Duration time) {
    }

    public record Plan(List<MaterialLine> materials, List<Component> components, List<MaterialLine> shopping,
                       List<Step> steps, double keptCost, double surplusCost, double surplusValue,
                       Surplus surplus) {

        public double materialsCost() {
            return keptCost + surplusCost;
        }

        public Duration extraTime() {
            return steps.stream().map(Step::time).reduce(Duration.ZERO, Duration::plus);
        }

        public Optional<Component> component(int typeId) {
            return components.stream().filter(component -> component.typeId() == typeId).findFirst();
        }
    }

    private record Recipe(IndustryActivity activity, boolean reaction, Facility facility, int me, int te,
                          long portion, double costIndex) {
    }

    private record Job(int runs, long produced, List<TypeQuantity> inputs, double fee, double seconds) {
    }

    public static Set<Integer> typesInvolved(List<Integer> roots,
                                             Function<Integer, Optional<IndustryActivity>> producer) {
        Set<Integer> seen = new LinkedHashSet<>(roots);
        Deque<Integer> pending = new ArrayDeque<>(roots);
        while (!pending.isEmpty()) {
            producer.apply(pending.removeFirst()).ifPresent(activity -> activity.materials().stream()
                    .map(TypeQuantity::typeId)
                    .filter(seen::add)
                    .forEach(pending::addLast));
        }
        return seen;
    }

    public static Plan plan(List<MaterialLine> materials, Setup setup, int lines, int reactionLines) {
        return new Planner(materials, setup).plan(lines, reactionLines);
    }

    private static final class Planner {

        private final List<MaterialLine> roots;
        private final Setup setup;
        private final Map<Integer, Optional<Recipe>> recipes = new HashMap<>();
        private final Map<Integer, Double> buildPrices = new HashMap<>();

        private Planner(List<MaterialLine> roots, Setup setup) {
            this.roots = roots;
            this.setup = setup;
        }

        private Plan plan(int lines, int reactionLines) {
            Round round = solve(type -> true);
            Map<Integer, Boolean> decisions = decide(round, Map.of());
            for (int i = 0; i < MAX_ROUNDS; i++) {
                Map<Integer, Boolean> current = decisions;
                round = solve(type -> current.getOrDefault(type, false));
                Map<Integer, Boolean> next = decide(round, current);
                if (next.equals(current)) {
                    break;
                }
                decisions = next;
            }
            return round.toPlan(lines, reactionLines);
        }

        private Map<Integer, Boolean> decide(Round round, Map<Integer, Boolean> previous) {
            Map<Integer, Boolean> decisions = new HashMap<>(previous);
            for (int type : round.order) {
                Optional<Recipe> recipe = recipe(type);
                if (recipe.isEmpty()) {
                    continue;
                }
                Boolean choice = setup.choices().get(type);
                if (choice != null) {
                    decisions.put(type, choice);
                    continue;
                }
                boolean build = switch (setup.mode()) {
                    case BUY -> false;
                    case CHEAPER -> cheaperToBuild(type);
                    case ALL -> recipe.get().reaction() || round.manufacturingInputs.contains(type);
                };
                decisions.put(type, build);
            }
            return decisions;
        }

        private boolean cheaperToBuild(int type) {
            Double price = buildPrices.get(type);
            double market = market(type);
            return price != null && (market <= 0 || price < market);
        }

        private Round solve(Predicate<Integer> builds) {
            Round round = new Round();
            for (MaterialLine root : roots) {
                round.manufacturingInputs.add(root.typeId());
                round.level(root.typeId(), builds, new HashSet<>());
                round.demand.merge(root.typeId(), root.quantity(), Long::sum);
            }
            List<Integer> built = round.levels.entrySet().stream()
                    .filter(entry -> entry.getValue() > 0)
                    .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
                    .map(Map.Entry::getKey)
                    .toList();
            for (int type : built) {
                Recipe recipe = recipe(type).orElseThrow();
                long need = round.demand.getOrDefault(type, 0L);
                int runs = (int) Math.max(1, Math.ceilDiv(need, recipe.portion()));
                List<TypeQuantity> inputs = new ArrayList<>();
                for (TypeQuantity material : recipe.activity().materials()) {
                    long quantity = IndustryCalculator.materialQuantity(material.quantity(), runs, recipe.me(),
                            recipe.facility().materialMultiplier());
                    inputs.add(new TypeQuantity(material.typeId(), quantity));
                    round.demand.merge(material.typeId(), quantity, Long::sum);
                }
                double fee = recipe.facility().jobFee(IndustryCalculator.estimatedItemValue(
                        recipe.activity().materials(), runs, setup.adjustedPrices()), recipe.costIndex());
                round.jobs.put(type, new Job(runs, runs * recipe.portion(), List.copyOf(inputs), fee,
                        seconds(recipe, runs)));
            }
            for (int type : built.reversed()) {
                Job job = round.jobs.get(type);
                double cost = job.fee();
                for (TypeQuantity input : job.inputs()) {
                    cost += input.quantity() * round.unitPrice(input.typeId());
                }
                double price = cost / job.produced();
                round.unitPrices.put(type, price);
                buildPrices.put(type, price);
            }
            return round;
        }

        private Optional<Recipe> recipe(int type) {
            return recipes.computeIfAbsent(type, id -> setup.producer().apply(id)
                    .filter(activity -> activity.product() != null)
                    .filter(activity -> activity.activityId() != IndustryActivity.REACTION || setup.reactionsAllowed())
                    .map(this::toRecipe));
        }

        private Recipe toRecipe(IndustryActivity activity) {
            boolean reaction = activity.activityId() == IndustryActivity.REACTION;
            return new Recipe(activity, reaction, reaction ? setup.reactionFacility() : setup.componentFacility(),
                    reaction ? 0 : setup.componentMe(), reaction ? 0 : setup.componentTe(),
                    Math.max(1, activity.product().quantity()),
                    reaction ? setup.reactionIndex() : setup.manufacturingIndex());
        }

        private double seconds(Recipe recipe, int runs) {
            double skills = recipe.reaction() ? IndustryCalculator.reactionTimeMultiplier(setup.level())
                    : IndustryCalculator.skillTimeMultiplier(recipe.activity().skills(), setup.level());
            return recipe.activity().timeSeconds() * runs * (1 - recipe.te() / 100.0)
                    * recipe.facility().timeMultiplier() * skills;
        }

        private double market(int type) {
            return setup.prices().getOrDefault(type, 0.0);
        }

        private final class Round {

            private final Set<Integer> order = new LinkedHashSet<>();
            private final Set<Integer> manufacturingInputs = new HashSet<>();
            private final Map<Integer, Integer> levels = new HashMap<>();
            private final Map<Integer, Long> demand = new HashMap<>();
            private final Map<Integer, Job> jobs = new HashMap<>();
            private final Map<Integer, Double> unitPrices = new HashMap<>();

            private int level(int type, Predicate<Integer> builds, Set<Integer> path) {
                Integer known = levels.get(type);
                if (known != null) {
                    return known;
                }
                if (path.contains(type)) {
                    return 0;
                }
                order.add(type);
                Optional<Recipe> recipe = recipe(type);
                if (recipe.isEmpty() || !builds.test(type) || path.size() >= MAX_DEPTH) {
                    levels.put(type, 0);
                    return 0;
                }
                path.add(type);
                int level = 0;
                for (TypeQuantity material : recipe.get().activity().materials()) {
                    if (!recipe.get().reaction()) {
                        manufacturingInputs.add(material.typeId());
                    }
                    level = Math.max(level, level(material.typeId(), builds, path));
                }
                path.remove(type);
                levels.put(type, level + 1);
                return level + 1;
            }

            private double unitPrice(int type) {
                Double price = unitPrices.get(type);
                return price != null ? price : market(type);
            }

            private Plan toPlan(int lines, int reactionLines) {
                List<MaterialLine> materials = new ArrayList<>();
                double keptCost = 0;
                for (MaterialLine root : roots) {
                    double price = unitPrice(root.typeId());
                    materials.add(new MaterialLine(root.typeId(), root.quantity(), price));
                    keptCost += root.quantity() * price;
                }
                List<Component> components = new ArrayList<>();
                List<MaterialLine> shopping = new ArrayList<>();
                double surplusValue = 0;
                double saleValue = 0;
                for (int type : order) {
                    long need = demand.getOrDefault(type, 0L);
                    Optional<Recipe> recipe = recipe(type);
                    int level = levels.getOrDefault(type, 0);
                    if (level == 0 && need > 0) {
                        shopping.add(new MaterialLine(type, need, market(type)));
                    }
                    if (recipe.isEmpty()) {
                        continue;
                    }
                    Job job = jobs.get(type);
                    int runs = job != null ? job.runs() : (int) Math.max(1, Math.ceilDiv(need, recipe.get().portion()));
                    Component component = new Component(type, recipe.get().reaction(), need, runs,
                            runs * recipe.get().portion(), market(type),
                            job != null ? unitPrice(type) : buildPrices.getOrDefault(type, market(type)), job != null,
                            Duration.ofSeconds(Math.round(job != null ? job.seconds() : seconds(recipe.get(), runs))),
                            level, job != null ? job.inputs() : List.of());
                    components.add(component);
                    surplusValue += component.surplus() * unitPrice(type);
                    if (market(type) > 0) {
                        saleValue += component.surplus() * market(type) * (1 - setup.sellFees());
                    }
                }
                double surplusCost = switch (setup.surplus()) {
                    case KEEP -> 0;
                    case SELL -> surplusValue - saleValue;
                    case WASTE -> surplusValue;
                };
                return new Plan(List.copyOf(materials), List.copyOf(components), List.copyOf(shopping),
                        steps(components, lines, reactionLines), keptCost, surplusCost, surplusValue,
                        setup.surplus());
            }

            private List<Step> steps(List<Component> components, int lines, int reactionLines) {
                Map<Integer, List<Component>> byLevel = new TreeMap<>();
                for (Component component : components) {
                    if (component.built()) {
                        byLevel.computeIfAbsent(component.step(), level -> new ArrayList<>()).add(component);
                    }
                }
                List<Step> steps = new ArrayList<>();
                for (List<Component> jobsInStep : byLevel.values()) {
                    List<Component> sorted = jobsInStep.stream()
                            .sorted(Comparator.comparing(Component::reaction).reversed())
                            .toList();
                    Duration reactions = elapsed(sorted.stream().filter(Component::reaction).toList(), reactionLines);
                    Duration manufacturing = elapsed(sorted.stream().filter(job -> !job.reaction()).toList(), lines);
                    steps.add(new Step(sorted, reactions.compareTo(manufacturing) > 0 ? reactions : manufacturing));
                }
                return List.copyOf(steps);
            }
        }
    }

    private static Duration elapsed(List<Component> jobs, int lines) {
        long longest = jobs.stream().mapToLong(job -> job.time().toSeconds()).max().orElse(0);
        long total = jobs.stream().mapToLong(job -> job.time().toSeconds()).sum();
        return Duration.ofSeconds(Math.max(longest, Math.ceilDiv(total, Math.max(1, lines))));
    }

    private BuildPlanner() {
    }
}
