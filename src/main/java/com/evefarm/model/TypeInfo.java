package com.evefarm.model;

public record TypeInfo(
        int typeId,
        String name,
        String groupName,
        String categoryName,
        double volume
) {
}
