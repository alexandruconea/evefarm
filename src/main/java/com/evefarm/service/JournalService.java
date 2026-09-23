package com.evefarm.service;

import com.evefarm.auth.AuthService;
import com.evefarm.db.dao.WalletJournalDao;
import com.evefarm.esi.WalletApi;
import com.evefarm.esi.dto.WalletJournalEntryDto;
import com.evefarm.model.JournalEntry;
import com.evefarm.model.JournalRow;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class JournalService {

    private final AuthService authService;
    private final WalletApi walletApi;
    private final EntityNameCacheService entityNameCacheService;
    private final WalletJournalDao walletJournalDao;

    public JournalService(AuthService authService, WalletApi walletApi,
                           EntityNameCacheService entityNameCacheService, WalletJournalDao walletJournalDao) {
        this.authService = authService;
        this.walletApi = walletApi;
        this.entityNameCacheService = entityNameCacheService;
        this.walletJournalDao = walletJournalDao;
    }

    public void refreshJournalForCharacter(long characterId) {
        String accessToken = authService.getValidAccessToken(characterId);
        List<WalletJournalEntryDto> entries = walletApi.listJournal(characterId, accessToken);

        Set<Long> partyIds = new HashSet<>();
        for (WalletJournalEntryDto entry : entries) {
            if (entry.firstPartyId() != null) {
                partyIds.add((long) entry.firstPartyId());
            }
            if (entry.secondPartyId() != null) {
                partyIds.add((long) entry.secondPartyId());
            }
        }
        entityNameCacheService.resolveEntities(partyIds);

        List<JournalEntry> journalEntries = entries.stream()
                .map(entry -> new JournalEntry(
                        entry.id(), entry.date(), entry.refType(),
                        entry.amount() == null ? 0 : entry.amount(),
                        entry.balance() == null ? 0 : entry.balance(),
                        entry.description(), entry.reason(), entry.firstPartyId(), entry.secondPartyId(),
                        entry.tax() == null ? 0 : entry.tax(), entry.taxReceiverId()))
                .toList();
        walletJournalDao.saveForCharacter(characterId, journalEntries);
    }

    public List<JournalRow> getJournalRows(Set<Long> characterIdFilter) {
        return walletJournalDao.listRows(characterIdFilter);
    }
}
