package com.evefarm.model;

public record IndustryJobEntry(
        long jobId,
        int activityId,
        String status,
        int blueprintTypeId,
        Integer productTypeId,
        Integer runs,
        Double cost,
        Long facilityId,
        Long outputLocationId,
        String startDate,
        String endDate
) {
}
