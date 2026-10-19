package com.evefarm.ui;

import com.evefarm.util.DateUtil;
import org.junit.jupiter.api.Test;

import javax.swing.JComboBox;
import javax.swing.JSpinner;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DateRangeControlsTest {

    @Test
    void quickDatesStartTheRangeThatFarBack() {
        LocalDate today = LocalDate.of(2026, 10, 19);

        assertEquals(LocalDate.of(2026, 10, 18), DateRangeControls.start("1 Day", today));
        assertEquals(LocalDate.of(2026, 10, 12), DateRangeControls.start("1 Week", today));
        assertEquals(LocalDate.of(2026, 9, 19), DateRangeControls.start("1 Month", today));
        assertEquals(LocalDate.of(2026, 4, 19), DateRangeControls.start("6 Months", today));
        assertEquals(LocalDate.of(2024, 10, 19), DateRangeControls.start("2 Years", today));
    }

    @Test
    void choosingAQuickDateSetsTheRangeAndShowsIt() {
        JComboBox<String> quickDate = new JComboBox<>();
        JSpinner from = new JSpinner();
        JSpinner to = new JSpinner();
        int[] shown = {0};
        DateRangeControls dates = new DateRangeControls(quickDate, from, to, () -> shown[0]++);

        quickDate.setSelectedItem("1 Week");

        LocalDate today = LocalDate.now();
        assertEquals(today.minusWeeks(1), dates.fromDate());
        assertEquals(today, dates.toDate());
        assertEquals(DateUtil.startOfDay(today.minusWeeks(1)), dates.from());
        assertEquals(DateUtil.endOfDay(today), dates.to());
        assertEquals(1, shown[0]);
    }
}
