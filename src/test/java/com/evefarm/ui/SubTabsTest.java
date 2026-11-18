package com.evefarm.ui;

import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.db.dao.SettingsDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubTabsTest {

    private final List<String> shown = new ArrayList<>();
    private SettingsDao settingsDao;

    private final class Page extends TabPanel {

        private final String name;

        Page(String name) {
            this.name = name;
        }

        @Override
        void onShown() {
            shown.add(name);
        }
    }

    @BeforeEach
    void setUp() {
        Database database = new Database(":memory:");
        MigrationRunner.run(database);
        settingsDao = new SettingsDao(database);
    }

    private SubTabs wallet() {
        return new SubTabs(settingsDao, "group.wallet",
                new SubTabs.Page("journal", "Journal", Icons.NOTEBOOK, new Page("journal")),
                new SubTabs.Page("transactions", "Transactions", Icons.SWAP, new Page("transactions")),
                new SubTabs.Page("marketOrders", "Market Orders", Icons.CLIPBOARD, new Page("marketOrders")));
    }

    private static List<String> titles(List<SubTabs.Page> pages) {
        return pages.stream().map(SubTabs.Page::title).toList();
    }

    @Test
    void onlyThePageYouSeeIsRefreshedAndNothingWhileBuilding() {
        SubTabs wallet = wallet();
        assertEquals(List.of(), shown);

        wallet.onShown();
        wallet.select(wallet.pages().get(2));
        wallet.onDataUpdated(true);

        assertEquals(List.of("journal", "marketOrders", "marketOrders"), shown);
        assertTrue(wallet.pages().get(2).panel().isVisible());
        assertFalse(wallet.pages().get(0).panel().isVisible());
    }

    @Test
    void itOpensOnThePageYouLeftItOn() {
        SubTabs wallet = wallet();
        wallet.select(wallet.pages().get(1));

        SubTabs reopened = wallet();
        reopened.onShown();

        assertEquals("transactions", reopened.selectedPage().key());
        assertEquals("transactions", shown.getLast());
    }

    @Test
    void hiddenPagesAreLeftOutAndAGroupWithNoneLeftIsNotShown() {
        SubTabs wallet = wallet();

        assertTrue(wallet.showPages(Set.of("journal")));
        assertEquals(List.of("Transactions", "Market Orders"), titles(wallet.visiblePages()));
        assertEquals("transactions", wallet.selectedPage().key());
        wallet.select(wallet.pages().getFirst());
        assertEquals("transactions", wallet.selectedPage().key(), "a hidden page can't be chosen");

        assertFalse(wallet.showPages(Set.of("journal", "transactions", "marketOrders")));
        assertTrue(wallet.showPages(Set.of()));
        assertEquals(3, wallet.visiblePages().size());
    }
}
