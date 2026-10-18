package com.evefarm.ui.column;

import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NumericAwareComparator implements Comparator<Object> {

    public static final NumericAwareComparator INSTANCE = new NumericAwareComparator();

    private static final String[] STRIPPABLE_SUFFIXES = {" ISK", "%", " m3", " jumps"};
    private static final Pattern FRACTION = Pattern.compile("^(-?\\d+) / \\d+$");
    private static final Pattern CLOCK = Pattern.compile("^(?:(\\d+):)?(\\d+):(\\d{2})$");
    private static final Pattern DURATION =
            Pattern.compile("^(?:(\\d+)d)? ?(?:(\\d+)h)? ?(?:(\\d+)m)? ?(?:(\\d+)s)?$");

    @Override
    public int compare(Object a, Object b) {
        Double numberA = toNumber(a);
        Double numberB = toNumber(b);
        if (numberA != null && numberB != null) {
            return Double.compare(numberA, numberB);
        }
        return String.valueOf(a).compareToIgnoreCase(String.valueOf(b));
    }

    private Double toNumber(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        String text = String.valueOf(value).trim();
        for (String suffix : STRIPPABLE_SUFFIXES) {
            if (text.endsWith(suffix)) {
                text = text.substring(0, text.length() - suffix.length()).trim();
                break;
            }
        }
        text = text.replace(",", "");
        if (text.matches("-?\\d+(\\.\\d+)?")) {
            return Double.parseDouble(text);
        }
        Matcher fraction = FRACTION.matcher(text);
        if (fraction.matches()) {
            return Double.parseDouble(fraction.group(1));
        }
        Matcher clock = CLOCK.matcher(text);
        if (clock.matches()) {
            return seconds(null, clock.group(1), clock.group(2), clock.group(3));
        }
        Matcher duration = DURATION.matcher(text);
        if (!text.isEmpty() && duration.matches()) {
            return seconds(duration.group(1), duration.group(2), duration.group(3), duration.group(4));
        }
        return null;
    }

    private static double seconds(String days, String hours, String minutes, String seconds) {
        return part(days) * 86_400 + part(hours) * 3_600 + part(minutes) * 60 + part(seconds);
    }

    private static double part(String digits) {
        return digits == null ? 0 : Double.parseDouble(digits);
    }

    private NumericAwareComparator() {
    }
}
