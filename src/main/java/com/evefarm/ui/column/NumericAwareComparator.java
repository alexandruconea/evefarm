package com.evefarm.ui.column;

import java.util.Comparator;

public final class NumericAwareComparator implements Comparator<Object> {

    public static final NumericAwareComparator INSTANCE = new NumericAwareComparator();

    private static final String[] STRIPPABLE_SUFFIXES = {" ISK", "%", " m3", " jumps"};

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
        if (!text.matches("-?\\d+(\\.\\d+)?")) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private NumericAwareComparator() {
    }
}
