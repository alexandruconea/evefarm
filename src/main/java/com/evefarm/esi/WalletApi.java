package com.evefarm.esi;

import com.evefarm.esi.dto.WalletJournalEntryDto;
import com.evefarm.esi.dto.WalletTransactionDto;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.List;
import java.util.Map;

public final class WalletApi {

    private final EsiHttpClient client;

    public WalletApi(EsiHttpClient client) {
        this.client = client;
    }

    public double getBalance(long characterId, String accessToken) {
        String body = client.get("/characters/" + characterId + "/wallet/", accessToken, Map.of());
        return client.read(body, Double.class, "wallet balance");
    }

    public List<WalletJournalEntryDto> listJournal(long characterId, String accessToken) {
        List<String> pages = client.getAllPages(
                "/characters/" + characterId + "/wallet/journal/", accessToken, Map.of());
        return client.readPages(pages, new TypeReference<List<WalletJournalEntryDto>>() {
        }, "wallet journal");
    }

    public List<WalletTransactionDto> listTransactions(long characterId, String accessToken) {
        String body = client.get("/characters/" + characterId + "/wallet/transactions/", accessToken, Map.of());
        return client.read(body, new TypeReference<List<WalletTransactionDto>>() {
        }, "wallet transactions");
    }
}
