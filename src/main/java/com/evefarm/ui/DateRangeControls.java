package com.evefarm.ui;

import com.evefarm.util.DateUtil;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JSpinner;
import javax.swing.SpinnerDateModel;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

final class DateRangeControls {

    private static final String[] QUICK_DATES = {
            "1 Day", "1 Week", "2 Weeks", "1 Month", "3 Months", "6 Months", "1 Year", "2 Years"};

    private final JSpinner fromSpinner;
    private final JSpinner toSpinner;

    DateRangeControls(JComboBox<String> quickDateCombo, JSpinner fromSpinner, JSpinner toSpinner,
                      Runnable onQuickDate) {
        this.fromSpinner = fromSpinner;
        this.toSpinner = toSpinner;
        quickDateCombo.setModel(new DefaultComboBoxModel<>(QUICK_DATES));
        quickDateCombo.addActionListener(e -> {
            LocalDate today = LocalDate.now();
            fromSpinner.setValue(DateUtil.toDate(start((String) quickDateCombo.getSelectedItem(), today)));
            toSpinner.setValue(DateUtil.toDate(today));
            onQuickDate.run();
        });
        for (JSpinner spinner : List.of(fromSpinner, toSpinner)) {
            spinner.setModel(new SpinnerDateModel());
            spinner.setEditor(new JSpinner.DateEditor(spinner, "yyyy-MM-dd"));
        }
    }

    static LocalDate start(String quickDate, LocalDate today) {
        return switch (quickDate) {
            case "1 Day" -> today.minusDays(1);
            case "1 Week" -> today.minusWeeks(1);
            case "2 Weeks" -> today.minusWeeks(2);
            case "3 Months" -> today.minusMonths(3);
            case "6 Months" -> today.minusMonths(6);
            case "1 Year" -> today.minusYears(1);
            case "2 Years" -> today.minusYears(2);
            default -> today.minusMonths(1);
        };
    }

    LocalDate fromDate() {
        return DateUtil.toLocalDate((Date) fromSpinner.getValue());
    }

    LocalDate toDate() {
        return DateUtil.toLocalDate((Date) toSpinner.getValue());
    }

    Instant from() {
        return DateUtil.startOfDay(fromDate());
    }

    Instant to() {
        return DateUtil.endOfDay(toDate());
    }
}
