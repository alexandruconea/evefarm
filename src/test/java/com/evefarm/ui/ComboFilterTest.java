package com.evefarm.ui;

import org.junit.jupiter.api.Test;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComboFilterTest {

    private static final List<String> CORPORATIONS = List.of("Amarr Navy", "Brutor Tribe",
            "Caldari Business Tribunal", "Caldari Navy", "CONCORD", "DED", "Duvolle Laboratories",
            "Federal Intelligence Office", "Federation Navy", "Ishukone Corporation", "Kaalakiota Corporation",
            "Lai Dai Corporation", "Republic Fleet", "Sisters of EVE");

    private static JComboBox<String> combo() {
        JComboBox<String> combo = new JComboBox<>(CORPORATIONS.toArray(new String[0]));
        combo.setSelectedItem("Republic Fleet");
        ComboFilter.install(combo);
        return combo;
    }

    private static void type(JComboBox<?> combo, String text) {
        for (char c : text.toCharArray()) {
            KeyEvent event = new KeyEvent(combo, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, c);
            for (KeyListener listener : combo.getKeyListeners()) {
                listener.keyTyped(event);
            }
        }
    }

    private static FilteredComboModel<?> model(JComboBox<?> combo) {
        return assertInstanceOf(FilteredComboModel.class, combo.getModel());
    }

    private static List<Object> items(JComboBox<?> combo) {
        return IntStream.range(0, combo.getItemCount()).mapToObj(combo::getItemAt).map(Object.class::cast).toList();
    }

    @Test
    void typingKeepsOnlyTheItemsWithEveryWordInAnyOrder() {
        JComboBox<String> combo = combo();

        type(combo, "navy");
        assertEquals(List.of("Amarr Navy", "Caldari Navy", "Federation Navy"), items(combo));

        model(combo).setFilter("navy cal");
        assertEquals(List.of("Caldari Navy"), items(combo));
        assertEquals("Republic Fleet", combo.getSelectedItem(), "filtering alone doesn't change the choice");
    }

    @Test
    void pickingAFilteredItemSelectsItWithTheWholeListBack() {
        JComboBox<String> combo = combo();
        List<String> seen = new ArrayList<>();
        combo.addActionListener(e -> seen.add(combo.getSelectedIndex() + ":" + combo.getSelectedItem()));
        type(combo, "navy");

        combo.setSelectedIndex(1);

        assertEquals(List.of("3:Caldari Navy"), seen);
        assertEquals(CORPORATIONS.size(), combo.getItemCount());
        assertFalse(model(combo).filtering());
    }

    @Test
    void clearingTheFilterFiresNoSelectionEvents() {
        JComboBox<String> combo = combo();
        List<Object> seen = new ArrayList<>();
        combo.addActionListener(e -> seen.add(combo.getSelectedItem()));
        combo.addItemListener(e -> seen.add(e.getItem()));
        type(combo, "corp");

        model(combo).setFilter("");

        assertEquals(List.of(), seen);
        assertEquals(CORPORATIONS, items(combo));
    }

    @Test
    void aNewListFromTheAppIsFilteredToo() {
        JComboBox<String> combo = combo();
        DefaultComboBoxModel<String> fresh = new DefaultComboBoxModel<>(CORPORATIONS.toArray(new String[0]));

        combo.setModel(fresh);
        combo.removeAllItems();
        CORPORATIONS.forEach(combo::addItem);
        type(combo, "tribe");

        assertEquals(List.of("Brutor Tribe"), items(combo));
        assertEquals(CORPORATIONS.size(), fresh.getSize());
    }

    @Test
    void shortListsKeepJumpingToTheFirstLetter() {
        JComboBox<String> combo = new JComboBox<>(new String[]{"Day", "Week", "Month"});
        ComboFilter.install(combo);

        assertTrue(combo.selectWithKeyChar('w'));
        assertEquals("Week", combo.getSelectedItem());
        assertEquals(3, combo.getItemCount());
    }
}
