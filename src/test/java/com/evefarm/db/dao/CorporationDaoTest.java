package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.model.AssetEntry;
import com.evefarm.model.AssetRow;
import com.evefarm.model.Corporation;
import com.evefarm.model.CorporationAccess;
import com.evefarm.model.JournalEntry;
import com.evefarm.model.JournalRow;
import com.evefarm.model.TransactionEntry;
import com.evefarm.model.TransactionRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorporationDaoTest {

    private static final long CORPORATION = 98_000_001L;
    private static final Instant NOW = Instant.parse("2026-11-28T10:00:00Z");

    private CorporationDao dao;

    @BeforeEach
    void setUp() {
        Database database = new Database(":memory:");
        MigrationRunner.run(database);
        new CharacterDao(database).upsert(1, "Alpha", List.of(), "a");
        dao = new CorporationDao(database);
        dao.saveCorporation(new Corporation(CORPORATION, "Deep Core Mining", "DCM"));
    }

    @Test
    void rolesAreKeptPerCharacter() {
        dao.saveAccess(new CorporationAccess(1, CORPORATION, List.of("Accountant", "Trader"), NOW));

        CorporationAccess access = dao.listAccess().getFirst();
        assertEquals(List.of("Accountant", "Trader"), access.roles());
        assertTrue(access.readsWallets());
        assertEquals(NOW, access.checkedAt());
    }

    @Test
    void walletsAreNamedAndAddedUp() {
        dao.saveDivisions(CORPORATION, CorporationDao.WALLET, Map.of(3, "Industry"));
        Map<Integer, Double> balances = new LinkedHashMap<>();
        balances.put(1, 1_000.0);
        balances.put(2, 250.0);
        balances.put(3, 50.0);
        dao.saveWallets(CORPORATION, balances);

        assertEquals(List.of(new CorporationDao.Wallet(1, "Master Wallet", 1_000), new CorporationDao.Wallet(2,
                "Wallet 2", 250), new CorporationDao.Wallet(3, "Industry", 50)), dao.wallets(CORPORATION));
        assertEquals(1_300, dao.walletsValue(CORPORATION), 1e-9);
    }

    @Test
    void assetsShowTheHangarDivisionAndTheCorporation() {
        dao.saveDivisions(CORPORATION, CorporationDao.HANGAR, Map.of(3, "Ore"));
        dao.replaceAssets(CORPORATION, List.of(
                new AssetEntry(10, 34, 1_000, 60003760L, "CorpSAG3", false, false, null, null, 5, 5_000),
                new AssetEntry(11, 35, 10, 60003760L, "CorpSAG1", false, false, null, null, 10, 100)));

        List<AssetRow> rows = dao.listAssetRows();

        assertEquals(List.of("Hangar 3: Ore", "Hangar 1"), rows.stream().map(AssetRow::locationFlag).toList());
        assertTrue(rows.stream().allMatch(AssetRow::corporation));
        assertEquals("Deep Core Mining [DCM]", rows.getFirst().characterName());
        assertEquals(5_100, dao.assetsValue(CORPORATION), 1e-9);
        assertEquals(11, dao.mostValuable(CORPORATION, null).orElseThrow().itemId(), "the highest unit price");
    }

    @Test
    void journalAndTransactionsSayWhichWalletTheyAreIn() {
        dao.saveJournal(CORPORATION, 1, List.of(new JournalEntry(7, "2026-11-27T10:00:00Z", "bounty_prizes", 1_000,
                5_000, "Bounty", null, null, null, 0, null)));
        dao.saveTransactions(CORPORATION, 2, List.of(new TransactionEntry(9, "2026-11-27T11:00:00Z", 34, 100, 5.0,
                null, 60003760L, false, false, 0)));

        JournalRow journal = dao.listJournalRows().getFirst();
        TransactionRow transaction = dao.listTransactionRows().getFirst();
        assertEquals("Deep Core Mining [DCM] · Master Wallet", journal.characterName());
        assertTrue(journal.corporation());
        assertEquals("Deep Core Mining [DCM] · Wallet 2", transaction.characterName());
        assertEquals(100, transaction.quantity());
    }
}
