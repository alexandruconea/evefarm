package com.evefarm.service;

import com.evefarm.auth.AuthService;
import com.evefarm.db.dao.WalletTransactionDao;
import com.evefarm.esi.WalletApi;
import com.evefarm.esi.dto.WalletTransactionDto;
import com.evefarm.model.TransactionEntry;
import com.evefarm.model.TransactionRow;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class TransactionService {

    private final AuthService authService;
    private final WalletApi walletApi;
    private final TypeNameCacheService typeNameCacheService;
    private final LocationNameCacheService locationNameCacheService;
    private final EntityNameCacheService entityNameCacheService;
    private final WalletTransactionDao walletTransactionDao;

    public TransactionService(AuthService authService, WalletApi walletApi,
                               TypeNameCacheService typeNameCacheService,
                               LocationNameCacheService locationNameCacheService,
                               EntityNameCacheService entityNameCacheService,
                               WalletTransactionDao walletTransactionDao) {
        this.authService = authService;
        this.walletApi = walletApi;
        this.typeNameCacheService = typeNameCacheService;
        this.locationNameCacheService = locationNameCacheService;
        this.entityNameCacheService = entityNameCacheService;
        this.walletTransactionDao = walletTransactionDao;
    }

    public void refreshTransactionsForCharacter(long characterId) {
        String accessToken = authService.getValidAccessToken(characterId);
        List<WalletTransactionDto> transactions = walletApi.listTransactions(characterId, accessToken);

        Set<Integer> typeIds = transactions.stream().map(WalletTransactionDto::typeId).collect(Collectors.toSet());
        typeNameCacheService.resolveTypes(typeIds);

        Set<Long> locationIds = transactions.stream().map(WalletTransactionDto::locationId)
                .collect(Collectors.toSet());
        locationNameCacheService.resolveLocations(locationIds, accessToken);

        Set<Long> clientIds = new HashSet<>();
        for (WalletTransactionDto transaction : transactions) {
            if (transaction.clientId() != null) {
                clientIds.add((long) transaction.clientId());
            }
        }
        entityNameCacheService.resolveEntities(clientIds);

        List<TransactionEntry> entries = transactions.stream()
                .map(t -> new TransactionEntry(t.transactionId(), t.date(), t.typeId(), t.quantity(), t.price(),
                        t.clientId(), t.locationId(), t.isBuy(), t.isPersonal(), t.journalRefId()))
                .toList();
        walletTransactionDao.saveForCharacter(characterId, entries);
    }

    public List<TransactionRow> getTransactionRows(Set<Long> characterIdFilter) {
        return walletTransactionDao.listRows(characterIdFilter);
    }
}
