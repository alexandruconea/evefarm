package com.evefarm.ui;

import com.evefarm.model.SpawnMember;
import com.evefarm.model.SpawnRow;
import com.evefarm.util.DateUtil;
import com.evefarm.util.IskFormatter;

import javax.swing.table.TableModel;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

final class SpawnReport {

    private static final String RULE = "=".repeat(80);
    private static final String GAP = "  ";

    private SpawnReport() {
    }

    static List<String> lines(List<SpawnRow> spawns, Function<SpawnRow, List<SpawnMember>> members, Instant saved) {
        List<String> lines = new ArrayList<>();
        lines.add("EVE Farm - Spawns");
        lines.add("Saved " + DateUtil.formatEveMinute(saved) + " EVE");
        int killed = spawns.stream().mapToInt(SpawnRow::killed).sum();
        double bounty = spawns.stream().mapToDouble(SpawnRow::bounty).sum();
        lines.add(String.format(Locale.US, "%,d spawn%s, %,d NPCs killed, %s bounty", spawns.size(),
                spawns.size() == 1 ? "" : "s", killed, IskFormatter.format(bounty)));
        spawns.stream().map(SpawnRow::startedAt).min(Comparator.naturalOrder()).ifPresent(first ->
                lines.add("From " + DateUtil.formatEveMinute(first) + " to " + DateUtil.formatEveMinute(
                        spawns.stream().map(SpawnRow::endedAt).max(Comparator.naturalOrder()).orElseThrow())
                        + " EVE"));
        for (int i = 0; i < spawns.size(); i++) {
            SpawnRow spawn = spawns.get(i);
            lines.add("");
            lines.add(RULE);
            lines.add(String.format(Locale.US, "Spawn %,d of %,d", i + 1, spawns.size()));
            lines.add(SpawnsPanel.headline(spawn));
            lines.add("Characters: " + spawn.characterName());
            if (spawn.contributions().size() > 1) {
                lines.add(SpawnsPanel.fightersDescription(spawn.contributions()));
            }
            lines.add("");
            SpawnTableModel model = new SpawnTableModel();
            model.setRows(members.apply(spawn));
            lines.addAll(table(model));
        }
        return lines;
    }

    static List<String> table(TableModel model) {
        int columns = model.getColumnCount();
        String[][] cells = new String[model.getRowCount()][columns];
        int[] widths = new int[columns];
        for (int column = 0; column < columns; column++) {
            widths[column] = model.getColumnName(column).length();
        }
        for (int row = 0; row < cells.length; row++) {
            for (int column = 0; column < columns; column++) {
                cells[row][column] = text(model.getValueAt(row, column));
                widths[column] = Math.max(widths[column], cells[row][column].length());
            }
        }
        boolean[] right = new boolean[columns];
        String[] header = new String[columns];
        String[] rule = new String[columns];
        for (int column = 0; column < columns; column++) {
            right[column] = alignRight(model, cells, column);
            header[column] = model.getColumnName(column);
            rule[column] = "-".repeat(widths[column]);
        }
        List<String> lines = new ArrayList<>();
        lines.add(line(header, widths, right));
        lines.add(line(rule, widths, right));
        for (String[] row : cells) {
            lines.add(line(row, widths, right));
        }
        return lines;
    }

    private static String text(Object value) {
        if (value instanceof Integer || value instanceof Long) {
            return String.format(Locale.US, "%,d", ((Number) value).longValue());
        }
        return value == null ? "" : value.toString();
    }

    private static boolean alignRight(TableModel model, String[][] cells, int column) {
        if (Number.class.isAssignableFrom(model.getColumnClass(column))) {
            return true;
        }
        boolean any = false;
        for (String[] row : cells) {
            if (!row[column].isEmpty()) {
                if (!row[column].endsWith(" ISK")) {
                    return false;
                }
                any = true;
            }
        }
        return any;
    }

    private static String line(String[] values, int[] widths, boolean[] right) {
        StringBuilder line = new StringBuilder();
        for (int column = 0; column < values.length; column++) {
            if (column > 0) {
                line.append(GAP);
            }
            String padding = " ".repeat(widths[column] - values[column].length());
            line.append(right[column] ? padding + values[column] : values[column] + padding);
        }
        return line.toString().stripTrailing();
    }
}
