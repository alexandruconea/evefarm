package com.evefarm.ui.column;

import com.evefarm.util.IskFormatter;

import java.util.Comparator;
import java.util.Objects;

public final class Isk extends Number implements Comparable<Isk> {

    public static final Isk NONE = new Isk(null, false);

    private static final Comparator<Double> ORDER = Comparator.nullsFirst(Comparator.naturalOrder());

    private final Double value;
    private final boolean signed;

    private Isk(Double value, boolean signed) {
        this.value = value;
        this.signed = signed;
    }

    public static Isk of(double value) {
        return new Isk(value, false);
    }

    public static Isk of(Double value) {
        return value == null ? NONE : new Isk(value, false);
    }

    public static Isk signed(double value) {
        return new Isk(value, true);
    }

    public static Isk signed(Double value) {
        return value == null ? NONE : new Isk(value, true);
    }

    public static String withUnit(String label) {
        if (label.contains("ISK")) {
            return label;
        }
        return label.endsWith(")") ? label.substring(0, label.length() - 1) + ", ISK)" : label + " (ISK)";
    }

    public boolean isEmpty() {
        return value == null;
    }

    public boolean isGain() {
        return signed && value != null && value > 0;
    }

    public boolean isLoss() {
        return signed && value != null && value < 0;
    }

    @Override
    public int intValue() {
        return (int) doubleValue();
    }

    @Override
    public long longValue() {
        return (long) doubleValue();
    }

    @Override
    public float floatValue() {
        return (float) doubleValue();
    }

    @Override
    public double doubleValue() {
        return value == null ? 0 : value;
    }

    @Override
    public int compareTo(Isk other) {
        return ORDER.compare(value, other.value);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Isk isk && Objects.equals(value, isk.value) && signed == isk.signed;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, signed);
    }

    @Override
    public String toString() {
        return value == null ? "" : IskFormatter.formatPlain(value);
    }
}
