package com.evefarm.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckListTest {

    private record Tab(String key, String label) {
    }

    @Test
    void itemsMoveUpAndDownAndKeepWhetherTheyAreTicked() {
        List<Tab> tabs = List.of(new Tab("assets", "Assets"), new Tab("journal", "Journal"), new Tab("abyss", "Abyss"));
        CheckList<Tab> list = new CheckList<>(tabs, List.of("journal"), Tab::key, Tab::label, tab -> null);

        list.list().setSelectedIndex(1);
        list.move(-1);
        list.move(-1);

        assertEquals(List.of("journal", "assets", "abyss"), list.items().stream().map(Tab::key).toList());
        assertEquals(0, list.list().getSelectedIndex());
        assertTrue(list.isChecked(tabs.get(1)));
        assertFalse(list.isChecked(tabs.get(0)));
    }

    @Test
    void everythingCanBeTickedOrUntickedAtOnce() {
        List<Tab> tabs = List.of(new Tab("assets", "Assets"), new Tab("journal", "Journal"));
        CheckList<Tab> list = new CheckList<>(tabs, List.of(), Tab::key, Tab::label, tab -> null);

        list.checkAll(true);
        assertTrue(tabs.stream().allMatch(list::isChecked));

        list.checkAll(false);
        assertTrue(tabs.stream().noneMatch(list::isChecked));
    }
}
