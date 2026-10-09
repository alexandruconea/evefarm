package com.evefarm.util;

import java.util.Locale;

public final class Text {

    public static String escapeHtml(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    public static String titleCase(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (String word : identifier.replaceAll("([a-z])([A-Z])", "$1 $2").split("[_ ]")) {
            if (word.isEmpty()) {
                continue;
            }
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(word.substring(0, 1).toUpperCase(Locale.US)).append(word.substring(1));
        }
        return result.toString();
    }

    private Text() {
    }
}
