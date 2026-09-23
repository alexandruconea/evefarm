package com.evefarm.esi;

import com.evefarm.esi.dto.WalletJournalEntryDto;
import com.evefarm.esi.dto.WalletTransactionDto;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class WalletApi {

    private final EsiHttpClient client;

    public WalletApi(EsiHttpClient client) {
        this.client = client;
    }

    public double getBalance(long characterId, String accessToken) {
        String body = client.get("/characters/" + characterId + "/wallet/", accessToken, Map.of());
        try {
            return client.objectMapper().readValue(body, Double.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse wallet balance", e);
        }
    }

    public List<WalletJournalEntryDto> listJournal(long characterId, String accessToken) {
        List<String> pages = client.getAllPages(
                "/characters/" + characterId + "/wallet/journal/", accessToken, Map.of());
        List<WalletJournalEntryDto> result = new ArrayList<>();
        try {
            for (String page : pages) {
                result.addAll(client.objectMapper().readValue(page, new TypeReference<List<WalletJournalEntryDto>>() {
                }));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse wallet journal", e);
        }
        return result;
    }

    public List<WalletTransactionDto> listTransactions(long characterId, String accessToken) {
        String body = client.get("/characters/" + characterId + "/wallet/transactions/", accessToken, Map.of());
        try {
            return client.objectMapper().readValue(body, new TypeReference<List<WalletTransactionDto>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse wallet transactions", e);
        }
    }
}
