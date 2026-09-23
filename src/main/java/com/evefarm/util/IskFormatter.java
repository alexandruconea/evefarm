package com.evefarm.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class IskFormatter {

    private static final ThreadLocal<DecimalFormat> FORMAT = ThreadLocal.withInitial(() -> {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        DecimalFormat format = new DecimalFormat("#,##0.00", symbols);
        return format;
    });

    public static String format(double isk) {
        return FORMAT.get().format(isk) + " ISK";
    }

    public static String formatPlain(double isk) {
        return FORMAT.get().format(isk);
    }

    private IskFormatter() {
    }
}
