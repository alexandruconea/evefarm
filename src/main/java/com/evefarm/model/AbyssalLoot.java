package com.evefarm.model;

public record AbyssalLoot(int typeId, String typeName, long quantity, Double unitPrice) {

    public double totalValue() {
        return unitPrice == null ? 0 : unitPrice * quantity;
    }
}
