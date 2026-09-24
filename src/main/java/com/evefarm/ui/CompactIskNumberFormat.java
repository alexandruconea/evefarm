package com.evefarm.ui;

import java.text.FieldPosition;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.Locale;

final class CompactIskNumberFormat extends NumberFormat {

    private final int decimals;

    CompactIskNumberFormat() {
        this(1);
    }

    CompactIskNumberFormat(int decimals) {
        this.decimals = decimals;
    }

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
        String number = String.format(Locale.US, "%,." + decimals + "f", value / divisor);
        if (number.contains(".")) {
            number = number.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return number + suffix;
    }
}
