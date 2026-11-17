package com.evefarm.service;

import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.db.dao.AssetDao;
import com.evefarm.db.dao.CharacterDao;
import com.evefarm.db.dao.LoyaltyPointDao;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.db.dao.SnapshotDao;
import com.evefarm.db.dao.TypeCacheDao;
import com.evefarm.db.dao.WalletJournalDao;
import com.evefarm.model.AssetEntry;
import com.evefarm.model.JournalEntry;
import com.evefarm.model.LoyaltyPointEntry;
import com.evefarm.model.TypeInfo;
import com.evefarm.model.ValueSummary;
import com.evefarm.model.ValueSummary.Today;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ValueSummaryServiceTest {

    private static final long MALPAIS = 2124165849L;
    private static final long SABINA = 2122998456L;
    private static final long JITA = 60003760L;
    private static final int THANATOS = 23911;
    private static final int RIFTER = 587;
    private static final int HARDENER = 15705;
    private static final int TRITANIUM = 34;
    private static final int AGENT = 3014399;
    private static final long GURISTAS = 1000127L;
    private static final ZoneId BUCHAREST = ZoneId.of("Europe/Bucharest");
    private static final Instant NOW = Instant.parse("2026-11-17T18:00:00Z");

    private ValueSummaryService values;
    private WalletJournalDao journal;
    private LoyaltyPointDao loyaltyPoints;
    private SettingsDao settings;

    @BeforeEach
    void setUp() {
        Database database = new Database(":memory:");
        MigrationRunner.run(database);
        CharacterDao characters = new CharacterDao(database);
        characters.upsert(MALPAIS, "Malpais Legate", List.of(), "owner");
        characters.upsert(SABINA, "Sabina Preda", List.of(), "owner");
        TypeCacheDao types = new TypeCacheDao(database);
        types.upsert(new TypeInfo(THANATOS, "Thanatos", "Carrier", "Ship", 1_000_000), 547, 6);
        types.upsert(new TypeInfo(RIFTER, "Rifter", "Frigate", "Ship", 27_289), 25, 6);
        types.upsert(new TypeInfo(HARDENER, "Corpus X-Type Thermal Armor Hardener", "Armor Hardener", "Module", 5),
                328, 7);
        types.upsert(new TypeInfo(TRITANIUM, "Tritanium", "Mineral", "Material", 0.01), 18, 4);
        AssetDao assets = new AssetDao(database);
        assets.replaceForCharacter(MALPAIS, List.of(
                asset(1, THANATOS, 1, 2_181_000_000.0),
                asset(2, HARDENER, 1, 348_400_000.0),
                asset(3, TRITANIUM, 900_000_000, 4.0)));
        assets.replaceForCharacter(SABINA, List.of(asset(4, RIFTER, 1, 500_000.0)));
        journal = new WalletJournalDao(database);
        loyaltyPoints = new LoyaltyPointDao(database);
        settings = new SettingsDao(database);
        NpcCatalogService npcs = mock(NpcCatalogService.class);
        when(npcs.catalog()).thenReturn(NpcCatalog.of(List.of()));
        values = new ValueSummaryService(mock(SnapshotDao.class), assets, mock(CharacterService.class), journal,
                loyaltyPoints, npcs, settings, Clock.fixed(NOW, BUCHAREST));
    }

    private static JournalEntry reward(long entryId, long characterId, String date, double amount) {
        return new JournalEntry(entryId, date, "agent_mission_reward", amount, 0, "Mission reward", "", AGENT,
                (int) characterId, 0, null);
    }

    private static JournalEntry timeBonus(long entryId, long characterId, String date, double amount) {
        return new JournalEntry(entryId, date, "agent_mission_time_bonus_reward", amount, 0, "Mission bonus", "",
                AGENT, (int) characterId, 0, null);
    }

    private static JournalEntry bounty(long entryId, long characterId, String date, double amount) {
        return new JournalEntry(entryId, date, "bounty_prizes", amount, 0, "Bounty", "", 1000125,
                (int) characterId, 0, null);
    }

    private void readLp(long characterId, String at, long points) {
        Instant dataFrom = Instant.parse(at);
        loyaltyPoints.replaceForCharacter(characterId, List.of(new LoyaltyPointEntry(GURISTAS, points)), dataFrom,
                dataFrom.plus(Duration.ofHours(1)));
    }

    private static AssetEntry asset(long itemId, int typeId, long quantity, double unitPrice) {
        return new AssetEntry(itemId, typeId, quantity, JITA, "Hangar", quantity == 1, false, null, null, unitPrice,
                unitPrice * quantity);
    }

    @Test
    void theMostValuableItemsAreThoseWithTheHighestUnitPrice() {
        ValueSummary everyone = values.summarizeGrandTotal();

        assertEquals("Thanatos", everyone.bestAssetName(), "a pile of minerals worth more in total doesn't count");
        assertEquals(2_181_000_000.0, everyone.bestAssetValue());
        assertEquals("Thanatos", everyone.bestShipName());
        assertEquals("Corpus X-Type Thermal Armor Hardener", everyone.bestModuleName());
        assertEquals(348_400_000.0, everyone.bestModuleValue());
    }

    @Test
    void aCharacterSeesOnlyTheirOwnItems() {
        ValueSummary sabina = values.summarizeCharacter(SABINA);

        assertEquals("Rifter", sabina.bestAssetName());
        assertEquals("Rifter", sabina.bestShipName());
        assertEquals(500_000.0, sabina.bestShipValue());
        assertNull(sabina.bestModuleName());
        assertEquals(0.0, sabina.bestModuleValue());
    }

    @Test
    void earnedTodayIsTheMissionIskAndLpOfTodayAsTheReportCountsThem() {
        journal.saveForCharacter(MALPAIS, List.of(
                reward(1, MALPAIS, "2026-11-16T12:00:00Z", 5_000_000),
                reward(2, MALPAIS, "2026-11-17T10:00:00Z", 2_000_000),
                timeBonus(3, MALPAIS, "2026-11-17T10:00:00Z", 1_000_000),
                bounty(4, MALPAIS, "2026-11-17T10:20:00Z", 500_000),
                bounty(5, MALPAIS, "2026-11-17T16:00:00Z", 300_000)));
        journal.saveForCharacter(SABINA, List.of(reward(6, SABINA, "2026-11-17T11:00:00Z", 1_000_000)));
        readLp(MALPAIS, "2026-11-16T20:00:00Z", 10_000);
        readLp(MALPAIS, "2026-11-17T09:00:00Z", 14_500);

        Today malpais = values.summarizeCharacter(MALPAIS).today();
        Today everyone = values.summarizeGrandTotal().today();

        assertEquals(3_500_000.0, malpais.isk(), "yesterday's reward and a ratting bounty far from a turn-in don't count");
        assertEquals(4_500L, malpais.lp());
        assertEquals(SettingsDao.DEFAULT_LP_STORE_TARGET_ISK_PER_LP, malpais.targetIskPerLp());
        assertEquals(3_500_000.0 + 4_500 * SettingsDao.DEFAULT_LP_STORE_TARGET_ISK_PER_LP, malpais.total());
        assertEquals(4_500_000.0, everyone.isk());
        assertEquals(4_500L, everyone.lp());
        assertEquals(1_000_000.0, values.summarizeCharacter(SABINA).today().isk());
    }

    @Test
    void todayStartsAtMidnightOnThisComputer() {
        journal.saveForCharacter(MALPAIS, List.of(
                reward(1, MALPAIS, "2026-11-16T21:59:00Z", 2_000_000),
                reward(2, MALPAIS, "2026-11-16T22:01:00Z", 3_000_000)));

        assertEquals(3_000_000.0, values.summarizeCharacter(MALPAIS).today().isk(),
                "22:00 UTC is midnight in Bucharest");
    }

    @Test
    void theLpCountsAtTheTargetSetInOffers() {
        settings.set(SettingsDao.LP_STORE_TARGET_ISK_PER_LP, "0");
        readLp(MALPAIS, "2026-11-16T20:00:00Z", 10_000);
        readLp(MALPAIS, "2026-11-17T09:00:00Z", 12_000);

        Today today = values.summarizeCharacter(MALPAIS).today();

        assertEquals(2_000L, today.lp());
        assertEquals(0.0, today.total(), "without a target the LP has no ISK value");
        assertTrue(values.summarizeCharacter(SABINA).today().isEmpty());
    }
}
