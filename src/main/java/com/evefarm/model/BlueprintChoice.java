package com.evefarm.model;

public record BlueprintChoice(int blueprintId, int productId, String productName, String groupName,
                              String categoryName) {

    @Override
    public String toString() {
        return productName;
    }
}
