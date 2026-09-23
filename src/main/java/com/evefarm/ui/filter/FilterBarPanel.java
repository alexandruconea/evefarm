package com.evefarm.ui.filter;

import com.evefarm.db.dao.SavedFilterDao;
import com.evefarm.ui.Icons;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.RowFilter;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class FilterBarPanel extends JPanel {

    private final String panelKey;
    private final List<String> columnNames;
    private final SavedFilterDao savedFilterDao;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<FilterRow> rows = new ArrayList<>();
    private final JPanel rowsPanel = new JPanel();
    private final JLabel countLabel = new JLabel(" ");
    private Runnable onFilterChanged = () -> {
    };

    public FilterBarPanel(String panelKey, List<String> columnNames, SavedFilterDao savedFilterDao) {
        super(new BorderLayout());
        this.panelKey = panelKey;
        this.columnNames = columnNames;
        this.savedFilterDao = savedFilterDao;

        JButton addButton = new JButton("Add", Icons.ADD);
        addButton.addActionListener(e -> {
            addRow(new FilterCondition());
            fireChanged();
        });
        JButton clearButton = new JButton("Clear", Icons.CLEAR);
        clearButton.addActionListener(e -> clearAll());
        JButton saveButton = new JButton("Save...", Icons.SAVE);
        saveButton.addActionListener(e -> saveCurrentFilter());
        JButton loadButton = new JButton("Load...", Icons.LOAD);
        loadButton.addActionListener(e -> loadSavedFilter());

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        toolbar.add(com.evefarm.ui.ButtonSizing.row(4, addButton, clearButton, saveButton, loadButton));
        toolbar.add(countLabel);

        rowsPanel.setLayout(new BoxLayout(rowsPanel, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        add(toolbar, BorderLayout.NORTH);
        add(rowsPanel, BorderLayout.CENTER);

        addRow(new FilterCondition());
    }

    public void setOnFilterChanged(Runnable onFilterChanged) {
        this.onFilterChanged = onFilterChanged;
    }

    public void setRowCounts(int shown, int total) {
        countLabel.setText("Showing " + shown + " of " + total);
    }

    private void addRow(FilterCondition initial) {
        FilterRow[] self = new FilterRow[1];
        FilterRow row = new FilterRow(columnNames, this::fireChanged, () -> {
            addRow(new FilterCondition());
            fireChanged();
        }, () -> removeRow(self[0]));
        self[0] = row;
        row.applyCondition(initial);
        rows.add(row);
        rowsPanel.add(row);
        renumberRows();
        revalidate();
        repaint();
    }

    private void removeRow(FilterRow row) {
        if (rows.size() <= 1) {
            return;
        }
        rows.remove(row);
        rowsPanel.remove(row);
        renumberRows();
        revalidate();
        repaint();
        fireChanged();
    }

    private void renumberRows() {
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).setFirstRow(i == 0);
            rows.get(i).setRemoveEnabled(rows.size() > 1);
        }
    }

    private void clearAll() {
        rows.clear();
        rowsPanel.removeAll();
        addRow(new FilterCondition());
        fireChanged();
    }

    private void fireChanged() {
        onFilterChanged.run();
    }

    public RowFilter<Object, Object> buildRowFilter() {
        List<FilterCondition> active = new ArrayList<>();
        for (FilterRow row : rows) {
            FilterCondition condition = row.toCondition();
            if (condition.isEnabled() && condition.getValue() != null && !condition.getValue().isBlank()) {
                active.add(condition);
            }
        }
        if (active.isEmpty()) {
            return null;
        }
        return new RowFilter<>() {
            @Override
            public boolean include(Entry<?, ?> entry) {
                boolean result = true;
                for (int i = 0; i < active.size(); i++) {
                    FilterCondition condition = active.get(i);
                    boolean matches = matches(entry, condition);
                    if (i == 0) {
                        result = matches;
                    } else if (condition.getLogicOp() == FilterCondition.LogicOp.OR) {
                        result = result || matches;
                    } else {
                        result = result && matches;
                    }
                }
                return result;
            }
        };
    }

    private boolean matches(RowFilter.Entry<?, ?> entry, FilterCondition condition) {
        if (FilterCondition.ALL_COLUMNS.equals(condition.getColumn())) {
            for (int col = 0; col < entry.getValueCount(); col++) {
                if (matchesCell(entry.getValue(col), condition)) {
                    return true;
                }
            }
            return false;
        }
        int col = columnNames.indexOf(condition.getColumn());
        if (col < 0) {
            return true;
        }
        return matchesCell(entry.getValue(col), condition);
    }

    private boolean matchesCell(Object cellValue, FilterCondition condition) {
        String text = String.valueOf(cellValue);
        String needle = condition.getValue();
        return switch (condition.getOperator()) {
            case CONTAINS -> text.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
            case NOT_EQUALS -> !text.equalsIgnoreCase(needle);
            case STARTS_WITH -> text.toLowerCase(Locale.ROOT).startsWith(needle.toLowerCase(Locale.ROOT));
            case ENDS_WITH -> text.toLowerCase(Locale.ROOT).endsWith(needle.toLowerCase(Locale.ROOT));
            case EQUALS -> text.equalsIgnoreCase(needle) || compareNumeric(cellValue, needle) == 0;
            case GREATER_THAN -> compareNumeric(cellValue, needle) > 0;
            case LESS_THAN -> compareNumeric(cellValue, needle) < 0;
        };
    }

    private int compareNumeric(Object cellValue, String needle) {
        Double cellNumber = toNumber(cellValue);
        Double needleNumber = toNumber(needle);
        if (cellNumber != null && needleNumber != null) {
            return Double.compare(cellNumber, needleNumber);
        }
        return String.valueOf(cellValue).compareToIgnoreCase(needle);
    }

    private Double toNumber(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        String text = String.valueOf(value).replaceAll("[^0-9.\\-]", "");
        if (text.isBlank() || "-".equals(text) || ".".equals(text)) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void saveCurrentFilter() {
        String name = JOptionPane.showInputDialog(this, "Filter name:", "Save Filter",
                JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.isBlank()) {
            return;
        }
        try {
            List<FilterCondition> conditions = rows.stream().map(FilterRow::toCondition).toList();
            String json = objectMapper.writeValueAsString(conditions);
            savedFilterDao.save(panelKey, name.trim(), json);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Failed to save filter: " + e.getMessage(),
                    "Save Filter", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadSavedFilter() {
        List<String> names = savedFilterDao.listNames(panelKey);
        if (names.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No saved filters yet.", "Load Filter",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JComboBox<String> combo = new JComboBox<>(names.toArray(new String[0]));
        int result = JOptionPane.showConfirmDialog(this, combo, "Load Filter",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return;
        }
        String selected = (String) combo.getSelectedItem();
        savedFilterDao.find(panelKey, selected).ifPresent(json -> {
            try {
                FilterCondition[] conditions = objectMapper.readValue(json, FilterCondition[].class);
                rows.clear();
                rowsPanel.removeAll();
                if (conditions.length == 0) {
                    addRow(new FilterCondition());
                } else {
                    for (FilterCondition condition : conditions) {
                        addRow(condition);
                    }
                }
                fireChanged();
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Failed to load filter: " + e.getMessage(),
                        "Load Filter", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
