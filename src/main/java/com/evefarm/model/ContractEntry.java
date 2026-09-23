package com.evefarm.model;

public record ContractEntry(
        long contractId,
        String type,
        String status,
        String title,
        Double collateral,
        Double price,
        Double reward,
        Double volume,
        String dateIssued,
        String dateExpired,
        String dateCompleted,
        boolean forCorporation,
        Integer issuerId,
        Integer assigneeId,
        Integer acceptorId,
        Long startLocationId,
        Long endLocationId
) {
}
