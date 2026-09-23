package com.evefarm.esi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record IndustryJobDto(
        @JsonProperty("job_id") long jobId,
        @JsonProperty("activity_id") int activityId,
        @JsonProperty("cost") Double cost,
        @JsonProperty("status") String status,
        @JsonProperty("blueprint_type_id") int blueprintTypeId,
        @JsonProperty("product_type_id") Integer productTypeId,
        @JsonProperty("runs") Integer runs,
        @JsonProperty("facility_id") Long facilityId,
        @JsonProperty("output_location_id") Long outputLocationId,
        @JsonProperty("start_date") String startDate,
        @JsonProperty("end_date") String endDate
) {
}
