package com.evefarm.esi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WalletTransactionDto(
        @JsonProperty("transaction_id") long transactionId,
        @JsonProperty("date") String date,
        @JsonProperty("type_id") int typeId,
        @JsonProperty("quantity") long quantity,
        @JsonProperty("unit_price") double price,
        @JsonProperty("client_id") Integer clientId,
        @JsonProperty("location_id") long locationId,
        @JsonProperty("is_buy") boolean isBuy,
        @JsonProperty("is_personal") boolean isPersonal,
        @JsonProperty("journal_ref_id") long journalRefId
) {
}
