package com.evefarm.service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CargoParser {

    private static final Pattern NAME_TIMES_QUANTITY = Pattern.compile("^(.+?)\\s+x\\s*([\\d.,'\\s\\u00A0]+)$");
    private static final Pattern QUANTITY_TIMES_NAME = Pattern.compile("^([\\d.,'\\u00A0]+)\\s*x\\s+(.+)$");

    private static final int MAX_COPY_LENGTH = 200_000;
    private static final int MAX_COPY_LINES = 2_000;

    private CargoParser() {
    }

    public static boolean looksLikeEveCopy(String text) {
        if (text == null || text.isBlank() || text.length() > MAX_COPY_LENGTH) {
            return false;
        }
        int lines = 0;
        for (String line : text.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            if (!line.contains("\t") || line.startsWith("\t") || ++lines > MAX_COPY_LINES) {
                return false;
            }
        }
        return lines > 0;
    }

    public static Map<String, Long> parse(String text) {
        Map<String, Long> quantities = new LinkedHashMap<>();
        Map<String, String> displayNames = new LinkedHashMap<>();
        if (text == null) {
            return quantities;
        }
        for (String rawLine : text.split("\\R")) {
            String line = rawLine.strip();
            if (line.isEmpty()) {
                continue;
            }
            String name;
            long quantity;
            if (line.contains("\t")) {
                String[] columns = line.split("\t");
                name = columns[0].strip();
                quantity = columns.length > 1 ? quantity(columns[1]) : 1;
            } else {
                Matcher nameFirst = NAME_TIMES_QUANTITY.matcher(line);
                Matcher quantityFirst = QUANTITY_TIMES_NAME.matcher(line);
                if (nameFirst.matches()) {
                    name = nameFirst.group(1).strip();
                    quantity = quantity(nameFirst.group(2));
                } else if (quantityFirst.matches()) {
                    name = quantityFirst.group(2).strip();
                    quantity = quantity(quantityFirst.group(1));
                } else {
                    name = line;
                    quantity = 1;
                }
            }
            if (name.isEmpty()) {
                continue;
            }
            String key = name.toLowerCase(Locale.ROOT);
            String displayName = displayNames.computeIfAbsent(key, ignored -> name);
            quantities.merge(displayName, quantity, Long::sum);
        }
        return quantities;
    }

    public static Map<String, Long> gained(Map<String, Long> before, Map<String, Long> after) {
        Map<String, Long> beforeByKey = new LinkedHashMap<>();
        before.forEach((name, quantity) -> beforeByKey.merge(name.toLowerCase(Locale.ROOT), quantity, Long::sum));
        Map<String, Long> gained = new LinkedHashMap<>();
        after.forEach((name, quantity) -> {
            long difference = quantity - beforeByKey.getOrDefault(name.toLowerCase(Locale.ROOT), 0L);
            if (difference > 0) {
                gained.put(name, difference);
            }
        });
        return gained;
    }

    private static long quantity(String text) {
        String digits = text.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return 1;
        }
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
