package com.evefarm.esi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WalletJournalEntryDto(
        @JsonProperty("id") long id,
        @JsonProperty("date") String date,
        @JsonProperty("ref_type") String refType,
        @JsonProperty("amount") Double amount,
        @JsonProperty("balance") Double balance,
        @JsonProperty("description") String description,
        @JsonProperty("reason") String reason,
        @JsonProperty("first_party_id") Integer firstPartyId,
        @JsonProperty("second_party_id") Integer secondPartyId,
        @JsonProperty("tax") Double tax,
        @JsonProperty("tax_receiver_id") Integer taxReceiverId
) {
}
