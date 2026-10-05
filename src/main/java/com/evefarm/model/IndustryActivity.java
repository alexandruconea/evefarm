package com.evefarm.model;

import java.util.List;
import java.util.Map;

public record IndustryActivity(
        int blueprintId,
        int activityId,
        long timeSeconds,
        List<TypeQuantity> materials,
        List<TypeQuantity> products,
        Map<Integer, Double> probabilities,
        List<SkillRequirement> skills
) {

    public static final int MANUFACTURING = 1;
    public static final int INVENTION = 8;
    public static final int REACTION = 11;

    public TypeQuantity product() {
        return products.isEmpty() ? null : products.getFirst();
    }

    public double probability(int productId) {
        return probabilities.getOrDefault(productId, 1.0);
    }
}
