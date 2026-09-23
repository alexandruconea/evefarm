package com.evefarm.util;

import java.util.ArrayList;
import java.util.List;

public final class CsvParsing {

    public static List<String> parseLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields;
    }

    public static List<List<String>> parseRecords(String text) {
        List<List<String>> records = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean recordHasContent = false;
        int start = text.startsWith("﻿") ? 1 : 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
                recordHasContent = true;
            } else if (c == ',') {
                fields.add(current.toString());
                current.setLength(0);
                recordHasContent = true;
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                if (recordHasContent) {
                    fields.add(current.toString());
                    records.add(fields);
                }
                fields = new ArrayList<>();
                current.setLength(0);
                recordHasContent = false;
            } else {
                current.append(c);
                recordHasContent = true;
            }
        }
        if (recordHasContent) {
            fields.add(current.toString());
            records.add(fields);
        }
        return records;
    }

    private CsvParsing() {
    }
}
