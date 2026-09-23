package com.evefarm.model;

public record IndustryJobRow(
        long characterId,
        String characterName,
        long jobId,
        int activityId,
        String status,
        String blueprintName,
        String productName,
        Integer runs,
        Double cost,
        String facilityName,
        String startDate,
        String endDate
) {
}
