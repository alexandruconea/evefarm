package com.evefarm.model;

public record ContractItem(
        int typeId,
        String name,
        String groupName,
        long quantity,
        boolean included,
        Double unitPrice
) {
    public Double totalValue() {
        return unitPrice == null ? null : unitPrice * quantity;
    }
}
