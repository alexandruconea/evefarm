package com.evefarm.esi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ContractDto(
        @JsonProperty("contract_id") long contractId,
        @JsonProperty("type") String type,
        @JsonProperty("status") String status,
        @JsonProperty("title") String title,
        @JsonProperty("collateral") Double collateral,
        @JsonProperty("price") Double price,
        @JsonProperty("reward") Double reward,
        @JsonProperty("volume") Double volume,
        @JsonProperty("date_issued") String dateIssued,
        @JsonProperty("date_expired") String dateExpired,
        @JsonProperty("date_completed") String dateCompleted,
        @JsonProperty("for_corporation") boolean forCorporation,
        @JsonProperty("issuer_id") Integer issuerId,
        @JsonProperty("assignee_id") Integer assigneeId,
        @JsonProperty("acceptor_id") Integer acceptorId,
        @JsonProperty("start_location_id") Long startLocationId,
        @JsonProperty("end_location_id") Long endLocationId
) {
}
