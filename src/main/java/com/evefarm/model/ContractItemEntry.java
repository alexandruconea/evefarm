package com.evefarm.model;

public record ContractItemEntry(
        long recordId,
        int typeId,
        long quantity,
        Integer rawQuantity,
        boolean included
) {
    public boolean blueprintCopy() {
        return rawQuantity != null && rawQuantity == -2;
    }
}
