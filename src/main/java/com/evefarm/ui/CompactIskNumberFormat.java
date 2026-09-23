package com.evefarm.ui;

import java.text.FieldPosition;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.Locale;

final class CompactIskNumberFormat extends NumberFormat {

    @Override
    public StringBuffer format(double number, StringBuffer toAppendTo, FieldPosition pos) {
        return toAppendTo.append(compactFormat(number));
    }

    @Override
    public StringBuffer format(long number, StringBuffer toAppendTo, FieldPosition pos) {
        return toAppendTo.append(compactFormat((double) number));
    }

    @Override
    public Number parse(String source, ParsePosition parsePosition) {
        return null;
    }

    private String compactFormat(double value) {
        double abs = Math.abs(value);
        double divisor;
        String suffix;
        if (abs >= 1_000_000_000_000d) {
            divisor = 1_000_000_000_000d;
            suffix = "T";
        } else if (abs >= 1_000_000_000d) {
            divisor = 1_000_000_000d;
            suffix = "B";
        } else if (abs >= 1_000_000d) {
            divisor = 1_000_000d;
            suffix = "M";
        } else if (abs >= 1_000d) {
            divisor = 1_000d;
            suffix = "K";
        } else {
            divisor = 1d;
            suffix = "";
        }
        String number = String.format(Locale.US, "%,.1f", value / divisor);
        if (number.endsWith(".0")) {
            number = number.substring(0, number.length() - 2);
        }
        return number + suffix;
    }
}
