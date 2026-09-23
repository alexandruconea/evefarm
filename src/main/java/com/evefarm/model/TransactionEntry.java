package com.evefarm.model;

public record TransactionEntry(
        long transactionId,
        String date,
        int typeId,
        long quantity,
        double price,
        Integer clientId,
        long locationId,
        boolean isBuy,
        boolean isPersonal,
        long journalRefId
) {
}
