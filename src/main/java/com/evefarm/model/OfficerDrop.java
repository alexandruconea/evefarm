package com.evefarm.model;

public record OfficerDrop(long id, String typeName, int quantity, double unitPrice) {

    public double totalValue() {
        return quantity * unitPrice;
    }
}
