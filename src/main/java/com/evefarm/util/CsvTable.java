package com.evefarm.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CsvTable {

    private final Map<String, Integer> columns = new HashMap<>();
    private final List<List<String>> rows;

    private CsvTable(List<List<String>> records) {
        if (records.isEmpty()) {
            rows = List.of();
            return;
        }
        List<String> header = records.getFirst();
        for (int i = 0; i < header.size(); i++) {
            columns.put(header.get(i).strip(), i);
        }
        rows = records.subList(1, records.size());
    }

    public static CsvTable parse(String text) {
        return new CsvTable(CsvParsing.parseRecords(text == null ? "" : text));
    }

    public List<List<String>> rows() {
        return rows;
    }

    public String text(List<String> row, String column) {
        Integer index = columns.get(column);
        if (index == null || index >= row.size()) {
            return null;
        }
        String value = row.get(index);
        return value == null || value.isBlank() || "None".equals(value) ? null : value.strip();
    }

    public Integer integer(List<String> row, String column) {
        Long value = number(row, column);
        return value == null || value > Integer.MAX_VALUE || value < Integer.MIN_VALUE ? null : value.intValue();
    }

    public Double decimal(List<String> row, String column) {
        String value = text(row, column);
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Long number(List<String> row, String column) {
        String value = text(row, column);
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            try {
                double decimal = Double.parseDouble(value);
                return decimal == Math.rint(decimal) ? (long) decimal : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
    }
}
