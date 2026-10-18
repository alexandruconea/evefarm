package com.evefarm.ui.column;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NumericAwareComparatorTest {

    private static List<String> sorted(String... values) {
        List<String> list = new ArrayList<>(List.of(values));
        list.sort(NumericAwareComparator.INSTANCE);
        return list;
    }

    @Test
    void iskAmountsSortByValue() {
        assertEquals(List.of("950.00 ISK", "1,200.50 ISK", "12,000,000.00 ISK"),
                sorted("12,000,000.00 ISK", "950.00 ISK", "1,200.50 ISK"));
    }

    @Test
    void trainingAndJobTimesSortByLength() {
        assertEquals(List.of("45s", "1m 41s", "1h 41m", "5d 10h 4m", "21d 5h 2m"),
                sorted("21d 5h 2m", "1h 41m", "5d 10h 4m", "45s", "1m 41s"));
    }

    @Test
    void runTimesSortByLength() {
        assertEquals(List.of("09:59", "12:34", "1:02:03"), sorted("1:02:03", "09:59", "12:34"));
    }

    @Test
    void orderQuantitiesSortByWhatIsLeft() {
        assertEquals(List.of("5 / 10", "10 / 10", "1,500 / 2,000"), sorted("10 / 10", "1,500 / 2,000", "5 / 10"));
    }

    @Test
    void wordsStillSortAlphabetically() {
        assertEquals(List.of("Caldari Navy", "No sales", "T3 Fierce"), sorted("T3 Fierce", "No sales", "Caldari Navy"));
    }
}
