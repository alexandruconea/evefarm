package com.evefarm.model;

public record ContractRow(
        long characterId,
        String characterName,
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
        String issuerName,
        String assigneeName,
        String acceptorName,
        String startLocationName,
        String endLocationName
) {
}
