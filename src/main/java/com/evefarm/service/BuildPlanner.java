package com.evefarm.service;

import com.evefarm.model.IndustryActivity;
import com.evefarm.model.TypeQuantity;
import com.evefarm.service.IndustryCalculator.Facility;
import com.evefarm.service.IndustryCalculator.MaterialLine;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;

public final class BuildPlanner {

    private static final int MAX_DEPTH = 6;

    public record Setup(Function<Integer, Optional<IndustryActivity>> producer, Map<Integer, Double> prices,
                        Map<Integer, Double> adjustedPrices, Facility componentFacility, Facility reactionFacility,
                        double manufacturingIndex, double reactionIndex, int componentMe, int componentTe,
                        IntUnaryOperator level, boolean reactionsAllowed, boolean buildWhenCheaper,
                        Map<Integer, Boolean> choices) {
    }

    public record Component(int typeId, boolean reaction, long needed, int runs, long produced, double marketPrice,
                            double buildPrice, boolean built, Duration time) {

        public double saving() {
            return (marketPrice - buildPrice) * needed;
        }

        private Component plus(Component other) {
            long total = needed + other.needed;
            double price = total > 0 ? (buildPrice * needed + other.buildPrice * other.needed) / total : buildPrice;
            return new Component(typeId, reaction, total, runs + other.runs, produced + other.produced,
                    marketPrice, price, built, time.plus(other.time));
        }
    }

    public record Plan(List<MaterialLine> materials, List<Component> components, List<MaterialLine> shopping,
                       double materialsCost, Duration componentTime, Duration reactionTime) {

        public Duration extraTime() {
            return componentTime.plus(reactionTime);
        }

        public Optional<Component> component(int typeId) {
            return components.stream().filter(component -> component.typeId() == typeId).findFirst();
        }
    }

    private record Evaluation(double cost, Collector collector) {
    }

    private static final class Collector {

        private final Map<Integer, Long> shopping = new LinkedHashMap<>();
        private final Map<Integer, Component> components = new LinkedHashMap<>();
        private final List<Double> componentJobs = new ArrayList<>();
        private final List<Double> reactionJobs = new ArrayList<>();

        private void buy(int typeId, long quantity) {
            shopping.merge(typeId, quantity, Long::sum);
        }

        private void component(Component component) {
            components.merge(component.typeId(), component, Component::plus);
        }

        private void add(Collector other) {
            other.shopping.forEach(this::buy);
            other.components.values().forEach(this::component);
            componentJobs.addAll(other.componentJobs);
            reactionJobs.addAll(other.reactionJobs);
        }
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
        Collector all = new Collector();
        List<MaterialLine> effective = new ArrayList<>();
        double cost = 0;
        for (MaterialLine line : materials) {
            Evaluation evaluation = evaluate(line.typeId(), line.quantity(), Set.of(), setup);
            cost += evaluation.cost();
            all.add(evaluation.collector());
            effective.add(new MaterialLine(line.typeId(), line.quantity(),
                    line.quantity() > 0 ? evaluation.cost() / line.quantity() : line.unitPrice()));
        }
        List<MaterialLine> shopping = new ArrayList<>();
        all.shopping.forEach((typeId, quantity) -> shopping.add(new MaterialLine(typeId, quantity,
                setup.prices().getOrDefault(typeId, 0.0))));
        return new Plan(List.copyOf(effective), List.copyOf(all.components.values()), List.copyOf(shopping), cost,
                elapsed(all.componentJobs, lines), elapsed(all.reactionJobs, reactionLines));
    }

    private static Evaluation evaluate(int typeId, long quantity, Set<Integer> path, Setup setup) {
        double market = setup.prices().getOrDefault(typeId, 0.0);
        IndustryActivity activity = path.size() < MAX_DEPTH && !path.contains(typeId)
                ? setup.producer().apply(typeId).orElse(null) : null;
        boolean reaction = activity != null && activity.activityId() == IndustryActivity.REACTION;
        if (activity == null || activity.product() == null || (reaction && !setup.reactionsAllowed())) {
            return bought(typeId, quantity, market);
        }
        Facility facility = reaction ? setup.reactionFacility() : setup.componentFacility();
        int me = reaction ? 0 : setup.componentMe();
        int te = reaction ? 0 : setup.componentTe();
        long portion = Math.max(1, activity.product().quantity());
        int runs = (int) Math.max(1, (quantity + portion - 1) / portion);
        Set<Integer> childPath = new HashSet<>(path);
        childPath.add(typeId);
        Collector children = new Collector();
        double childCost = 0;
        for (TypeQuantity material : activity.materials()) {
            long needed = IndustryCalculator.materialQuantity(material.quantity(), runs, me,
                    facility.materialMultiplier());
            Evaluation child = evaluate(material.typeId(), needed, childPath, setup);
            childCost += child.cost();
            children.add(child.collector());
        }
        double fee = facility.jobFee(IndustryCalculator.estimatedItemValue(activity.materials(), runs,
                setup.adjustedPrices()), reaction ? setup.reactionIndex() : setup.manufacturingIndex());
        long produced = runs * portion;
        double buildPrice = (childCost + fee) / produced;
        double skills = reaction ? IndustryCalculator.reactionTimeMultiplier(setup.level())
                : IndustryCalculator.skillTimeMultiplier(activity.skills(), setup.level());
        double seconds = activity.timeSeconds() * runs * (1 - te / 100.0) * facility.timeMultiplier() * skills;
        boolean built = setup.choices().getOrDefault(typeId,
                setup.buildWhenCheaper() && (market <= 0 || buildPrice < market));
        Collector collector = new Collector();
        collector.component(new Component(typeId, reaction, quantity, runs, produced, market, buildPrice, built,
                Duration.ofSeconds(Math.round(seconds))));
        if (!built) {
            collector.buy(typeId, quantity);
            return new Evaluation(market * quantity, collector);
        }
        collector.add(children);
        (reaction ? collector.reactionJobs : collector.componentJobs).add(seconds);
        return new Evaluation(buildPrice * quantity, collector);
    }

    private static Evaluation bought(int typeId, long quantity, double market) {
        Collector collector = new Collector();
        collector.buy(typeId, quantity);
        return new Evaluation(market * quantity, collector);
    }

    private static Duration elapsed(List<Double> jobs, int lines) {
        double longest = jobs.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double total = jobs.stream().mapToDouble(Double::doubleValue).sum();
        return Duration.ofSeconds(Math.round(Math.max(longest, total / Math.max(1, lines))));
    }

    private BuildPlanner() {
    }
}
