package com.evefarm.model;

public record BlueprintChoice(int blueprintId, String productName, String groupName) {

    @Override
    public String toString() {
        return productName;
    }
}
