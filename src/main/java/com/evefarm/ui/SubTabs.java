package com.evefarm.ui;

import com.evefarm.db.dao.SettingsDao;

import javax.swing.Icon;
import java.awt.CardLayout;
import java.util.List;
import java.util.Optional;
import java.util.Set;

class SubTabs extends TabPanel {

    record Page(String key, String title, Icon icon, TabPanel panel, String toolTip) {

        Page(String key, String title, Icon icon, TabPanel panel) {
            this(key, title, icon, panel, null);
        }
    }

    private final CardLayout cards = new CardLayout();
    private final List<Page> pages;
    private final SettingsDao settingsDao;
    private final String memoryKey;
    private List<Page> visiblePages;
    private Page selected;

    SubTabs(SettingsDao settingsDao, String key, Page... pages) {
        this.settingsDao = settingsDao;
        this.memoryKey = SettingsDao.SUB_TAB_PREFIX + key;
        this.pages = List.of(pages);
        setLayout(cards);
        for (Page page : pages) {
            add(page.panel(), page.key());
        }
        showPages(Set.of());
    }

    List<Page> pages() {
        return pages;
    }

    List<Page> visiblePages() {
        return visiblePages;
    }

    Page selectedPage() {
        return selected;
    }

    void select(Page page) {
        if (!visiblePages.contains(page)) {
            return;
        }
        show(page);
        settingsDao.set(memoryKey, page.key());
        page.panel().onShown();
    }

    boolean showPages(Set<String> hiddenKeys) {
        visiblePages = pages.stream().filter(page -> !hiddenKeys.contains(page.key())).toList();
        if (visiblePages.isEmpty()) {
            return false;
        }
        if (selected == null || !visiblePages.contains(selected)) {
            Page previous = selected;
            show(remembered().filter(visiblePages::contains).orElse(visiblePages.getFirst()));
            if (previous != null && isShowing()) {
                selected.panel().onShown();
            }
        }
        return true;
    }

    @Override
    void onShown() {
        if (selected != null) {
            selected.panel().onShown();
        }
    }

    @Override
    void refreshCharacterFilter() {
        pages.forEach(page -> page.panel().refreshCharacterFilter());
        onShown();
    }

    @Override
    void onDataUpdated(boolean selectedTab) {
        if (selected != null) {
            selected.panel().onDataUpdated(selectedTab);
        }
    }

    private void show(Page page) {
        selected = page;
        cards.show(this, page.key());
    }

    private Optional<Page> remembered() {
        return settingsDao.get(memoryKey)
                .flatMap(key -> pages.stream().filter(page -> page.key().equals(key)).findFirst());
    }
}
