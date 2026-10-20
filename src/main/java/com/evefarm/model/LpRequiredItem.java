package com.evefarm.model;

public record LpRequiredItem(
        int typeId,
        String itemName,
        long quantity
) {
}
