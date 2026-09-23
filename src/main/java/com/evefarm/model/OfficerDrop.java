package com.evefarm.model;

import java.time.Instant;

public record OfficerDrop(long id, int typeId, String typeName, int quantity, double unitPrice, Instant addedAt) {

    public double totalValue() {
        return quantity * unitPrice;
    }
}
